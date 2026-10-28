/**
 * load-balancer.js - Dynamic Load Balancer & Task Allocation Dashboard (Exp 6)
 * Author: Parth (Compute & Analytics Backend)
 */

(function () {
  'use strict';

  // State
  let candidateNodes = [];
  let pendingTasks = [];
  let assignedTasks = [];
  let latestResult = null;
  const tracesByTaskId = {};

  // DOM Elements
  const nodesContainer = document.getElementById('nodes-container');
  const pendingTbody = document.getElementById('pending-tasks-tbody');
  const assignedTbody = document.getElementById('assigned-tasks-tbody');
  const traceTerminal = document.getElementById('trace-terminal');
  const traceSummary = document.getElementById('trace-summary');
  const traceMetaBadge = document.getElementById('trace-meta-badge');
  const evalBreakdownWrap = document.getElementById('eval-breakdown-wrap');
  const evalTableBody = document.getElementById('eval-table-body');
  const liveEventsLog = document.getElementById('live-events-log');

  // KPI Elements
  const kpiActiveNodes = document.getElementById('kpi-active-nodes');
  const kpiNodesBadge = document.getElementById('kpi-nodes-badge');
  const kpiNodesSub = document.getElementById('kpi-nodes-sub');
  const kpiPendingTasks = document.getElementById('kpi-pending-tasks');
  const kpiPendingWorkload = document.getElementById('kpi-pending-workload');
  const kpiAssignedTasks = document.getElementById('kpi-assigned-tasks');
  const kpiAssignedWorkload = document.getElementById('kpi-assigned-workload');
  const kpiAvgUtilization = document.getElementById('kpi-avg-utilization');

  // Initialize
  document.addEventListener('DOMContentLoaded', () => {
    initButtons();
    initForm();
    initEvents();
    refreshAll();
  });

  function initButtons() {
    const btnSeed = document.getElementById('btn-seed-tasks');
    const btnQuickSeed = document.getElementById('btn-quick-seed');
    const btnAssignAll = document.getElementById('btn-assign-all');
    const btnRefresh = document.getElementById('btn-refresh');
    const btnResetSim = document.getElementById('btn-reset-sim');
    const btnClearTasks = document.getElementById('btn-clear-tasks');

    if (btnSeed) btnSeed.addEventListener('click', seedSampleTasks);
    if (btnQuickSeed) btnQuickSeed.addEventListener('click', seedSampleTasks);
    if (btnAssignAll) btnAssignAll.addEventListener('click', assignAllPending);
    if (btnRefresh) btnRefresh.addEventListener('click', refreshAll);
    if (btnResetSim) btnResetSim.addEventListener('click', resetSimulation);
    if (btnClearTasks) btnClearTasks.addEventListener('click', clearAllTasks);
  }

  function initForm() {
    const form = document.getElementById('task-form');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const descInput = document.getElementById('task-desc');
      const workloadInput = document.getElementById('task-workload');
      const typeInput = document.getElementById('task-type');
      const clusterInput = document.getElementById('task-cluster');
      const idInput = document.getElementById('task-id-custom');

      const desc = descInput.value.trim();
      const workload = parseFloat(workloadInput.value) || 1.0;
      const type = typeInput.value;
      const clusterId = clusterInput.value;
      const customId = idInput.value.trim();

      if (!desc) {
        if (window.toast) window.toast.warning('Validation', 'Please provide a task description.');
        return;
      }

      const payload = {
        taskId: customId || null,
        description: desc,
        workload: workload,
        clusterId: clusterId,
        type: type
      };

      try {
        const created = await window.api.post('/api/tasks', payload);
        if (window.toast) {
          window.toast.success('Task Created', `Added ${created.taskId || 'task'} to the pending queue.`);
        }
        descInput.value = '';
        idInput.value = '';
        await refreshTasks();
      } catch (err) {
        console.error('Failed to create task:', err);
        // Fallback for mock mode
        if (window.api && window.api.isMock()) {
          const mockId = customId || `TASK-${String(pendingTasks.length + 1).padStart(2, '0')}`;
          pendingTasks.push({
            taskId: mockId,
            description: desc,
            workload: workload,
            type: type,
            clusterId: clusterId,
            status: 'PENDING',
            createdAt: new Date().toISOString()
          });
          renderTasks();
          updateKpis();
          if (window.toast) window.toast.success('Task Created (Mock)', `Added ${mockId} to queue.`);
        }
      }
    });
  }

  function initEvents() {
    if (!window.events) return;

    window.events.on('compute', 'TASK_CREATED', (payload) => {
      logLiveEvent('TASK_CREATED', payload);
      refreshTasks();
    });

    window.events.on('compute', 'TASK_ASSIGNED', (payload) => {
      logLiveEvent('TASK_ASSIGNED', payload);
      if (payload && payload.taskId) {
        tracesByTaskId[payload.taskId] = payload;
      }
      refreshAll();
    });
  }

  function logLiveEvent(type, payload) {
    if (!liveEventsLog) return;
    const line = document.createElement('div');
    line.className = 'trace-step-line';
    const now = new Date().toLocaleTimeString();
    let text = `[${now}] ${type}: `;
    if (typeof payload === 'object') {
      if (type === 'TASK_ASSIGNED') {
        text += `Task ${payload.taskId} assigned to ${payload.chosenNodeId || 'NONE'} (Updated load: ${payload.updatedLoad != null ? payload.updatedLoad.toFixed(2) : '--'})`;
      } else if (type === 'TASK_CREATED') {
        text += `Task ${payload.taskId || 'new'} created (${payload.description || ''}, workload: ${payload.workload || ''})`;
      } else {
        text += JSON.stringify(payload);
      }
    } else {
      text += String(payload);
    }
    line.textContent = text;
    liveEventsLog.prepend(line);
  }

  async function refreshAll() {
    await Promise.all([refreshNodes(), refreshTasks()]);
  }

  async function refreshNodes() {
    try {
      const data = await window.api.get('/api/loadbalancer/nodes');
      candidateNodes = Array.isArray(data) ? data : [];
      renderNodes();
      updateKpis();
    } catch (err) {
      console.warn('Could not fetch /api/loadbalancer/nodes, using fallback stubs:', err);
      if (candidateNodes.length === 0) {
        candidateNodes = [
          { nodeId: 'UAV-01', batteryLevel: 80, signalStrength: 70, maxCapacity: 10.0, capacity: 10.0, effectiveCapacity: 7.50, currentLoad: 0.0, load: 0.0, utilization: 0.0, available: true, clusterId: 'Cluster-A', nodeType: 'UAV' },
          { nodeId: 'UAV-02', batteryLevel: 60, signalStrength: 55, maxCapacity: 10.0, capacity: 10.0, effectiveCapacity: 5.75, currentLoad: 0.0, load: 0.0, utilization: 0.0, available: true, clusterId: 'Cluster-A', nodeType: 'UAV' },
          { nodeId: 'Edge-01', batteryLevel: 90, signalStrength: 85, maxCapacity: 15.0, capacity: 15.0, effectiveCapacity: 13.13, currentLoad: 0.0, load: 0.0, utilization: 0.0, available: true, clusterId: 'Cluster-B', nodeType: 'EDGE' },
          { nodeId: 'Edge-02', batteryLevel: 85, signalStrength: 75, maxCapacity: 15.0, capacity: 15.0, effectiveCapacity: 12.00, currentLoad: 0.0, load: 0.0, utilization: 0.0, available: true, clusterId: 'Cluster-B', nodeType: 'EDGE' }
        ];
      }
      renderNodes();
      updateKpis();
    }
  }

  async function refreshTasks() {
    try {
      const data = await window.api.get('/api/tasks');
      const allTasks = Array.isArray(data) ? data : [];
      pendingTasks = allTasks.filter(t => (t.status || '').toUpperCase() === 'PENDING');
      assignedTasks = allTasks.filter(t => (t.status || '').toUpperCase() === 'ASSIGNED');
      renderTasks();
      updateKpis();
    } catch (err) {
      console.warn('Could not fetch /api/tasks:', err);
      renderTasks();
      updateKpis();
    }
  }

  function renderNodes() {
    if (!nodesContainer) return;
    nodesContainer.innerHTML = '';

    if (candidateNodes.length === 0) {
      nodesContainer.innerHTML = `<div style="grid-column: 1/-1; text-align: center; padding: 24px; color: var(--text-muted);">No candidate nodes registered.</div>`;
      return;
    }

    candidateNodes.forEach(node => {
      const card = document.createElement('div');
      const isAvail = node.available !== false && (node.status || '').toUpperCase() !== 'UNAVAILABLE';
      card.className = `node-card ${isAvail ? 'available' : 'unavailable'}`;
      card.id = `node-card-${node.nodeId}`;

      const effCap = node.effectiveCapacity != null ? Number(node.effectiveCapacity).toFixed(2) : '--';
      const maxCap = node.capacity != null ? Number(node.capacity).toFixed(1) : (node.maxCapacity != null ? Number(node.maxCapacity).toFixed(1) : '--');
      const curLoad = node.load != null ? Number(node.load).toFixed(2) : (node.currentLoad != null ? Number(node.currentLoad).toFixed(2) : '0.00');

      let utilFrac = 0;
      if (node.utilization != null && !isNaN(node.utilization) && isFinite(node.utilization)) {
        utilFrac = Number(node.utilization);
      } else if (Number(effCap) > 0) {
        utilFrac = Number(curLoad) / Number(effCap);
      }
      const utilPct = Math.min(100, Math.max(0, Math.round(utilFrac * 100)));

      let progressColorClass = 'progress-success';
      if (utilPct > 80) {
        progressColorClass = 'progress-danger';
      } else if (utilPct > 50) {
        progressColorClass = 'progress-warning';
      }

      const bat = node.batteryLevel != null ? node.batteryLevel : (node.battery != null ? node.battery : '--');
      const sig = node.signalStrength != null ? node.signalStrength : (node.signal != null ? node.signal : '--');
      const type = node.nodeType || (node.nodeId.toLowerCase().includes('edge') ? 'EDGE' : 'UAV');
      const cluster = node.clusterId || 'Cluster-A';

      card.innerHTML = `
        <div class="node-card-header">
          <div class="node-title">
            <span>${type === 'EDGE' ? '🖥️' : '🛸'}</span>
            <span>${escapeHtml(node.nodeId)}</span>
            <span class="badge ${type === 'EDGE' ? 'badge-blue' : 'badge-green'}" style="font-size: 0.68rem;">${type}</span>
          </div>
          <div>
            <span class="badge ${isAvail ? 'badge-green' : 'badge-red'}" id="badge-status-${node.nodeId}">
              ${isAvail ? 'AVAILABLE' : 'OFFLINE'}
            </span>
          </div>
        </div>

        <div style="font-size: 0.76rem; color: var(--text-muted); margin-bottom: 10px;">
          📍 ${escapeHtml(cluster)}
        </div>

        <div class="node-metrics-grid">
          <div class="metric-item">
            🔋 Battery: <strong>${bat}%</strong>
          </div>
          <div class="metric-item">
            📶 Signal: <strong>${sig}%</strong>
          </div>
          <div class="metric-item">
            ⚖️ Max Cap: <strong>${maxCap}</strong>
          </div>
          <div class="metric-item">
            ⚡ Eff. Cap: <strong>${effCap}</strong>
          </div>
        </div>

        <div class="utilization-section">
          <div class="utilization-header">
            <span>Current Load: <strong>${curLoad}</strong></span>
            <span>Util: <strong>${utilPct}%</strong></span>
          </div>
          <div class="progress" style="height: 10px;">
            <div id="bar-${node.nodeId}" class="progress-bar progress-bar-animated ${progressColorClass}" style="width: ${utilPct}%;"></div>
          </div>
        </div>

        <div class="node-card-actions">
          <label class="toggle-switch-label" title="Toggle node availability to demonstrate re-balancing on failure">
            <input type="checkbox" id="toggle-${node.nodeId}" ${isAvail ? 'checked' : ''}>
            <span>${isAvail ? 'Available for Dispatch' : 'Marked Offline'}</span>
          </label>
        </div>
      `;

      // Wire availability toggle
      const checkbox = card.querySelector(`#toggle-${node.nodeId}`);
      if (checkbox) {
        checkbox.addEventListener('change', async () => {
          await toggleNodeAvailability(node.nodeId, checkbox.checked);
        });
      }

      nodesContainer.appendChild(card);
    });
  }

  function renderTasks() {
    renderPendingTable();
    renderAssignedTable();
  }

  function renderPendingTable() {
    if (!pendingTbody) return;
    pendingTbody.innerHTML = '';

    if (pendingTasks.length === 0) {
      pendingTbody.innerHTML = `
        <tr>
          <td colspan="7" style="text-align: center; padding: 24px; color: var(--text-muted);">
            No pending rescue tasks. Click <strong>🌱 Load Sample Tasks</strong> or submit the form above.
          </td>
        </tr>`;
      return;
    }

    pendingTasks.forEach(task => {
      const tr = document.createElement('tr');
      const typeBadge = getTaskTypeBadge(task.type);
      const createdTime = task.createdAt ? new Date(task.createdAt).toLocaleTimeString() : '--';

      tr.innerHTML = `
        <td><strong>${escapeHtml(task.taskId)}</strong></td>
        <td>${escapeHtml(task.description || '')}</td>
        <td><span class="badge badge-info" style="font-weight: 700;">${Number(task.workload).toFixed(1)}</span></td>
        <td><span class="badge ${typeBadge}">${escapeHtml(task.type || 'General')}</span></td>
        <td>${escapeHtml(task.clusterId || 'Cluster-A')}</td>
        <td style="color: var(--text-muted); font-size: 0.8rem;">${createdTime}</td>
        <td style="text-align: right;">
          <button class="btn btn-sm btn-primary btn-assign-single" data-task-id="${task.taskId}">
            ⚡ Assign
          </button>
        </td>
      `;

      const btn = tr.querySelector('.btn-assign-single');
      if (btn) {
        btn.addEventListener('click', () => assignSingleTask(task.taskId));
      }

      pendingTbody.appendChild(tr);
    });
  }

  function renderAssignedTable() {
    if (!assignedTbody) return;
    assignedTbody.innerHTML = '';

    if (assignedTasks.length === 0) {
      assignedTbody.innerHTML = `
        <tr>
          <td colspan="8" style="text-align: center; padding: 24px; color: var(--text-muted);">
            No assigned tasks yet.
          </td>
        </tr>`;
      return;
    }

    assignedTasks.forEach(task => {
      const tr = document.createElement('tr');
      const typeBadge = getTaskTypeBadge(task.type);
      const assignedTime = task.assignedAt ? new Date(task.assignedAt).toLocaleTimeString() : (task.createdAt ? new Date(task.createdAt).toLocaleTimeString() : '--');

      tr.innerHTML = `
        <td><strong>${escapeHtml(task.taskId)}</strong></td>
        <td>${escapeHtml(task.description || '')}</td>
        <td><span class="badge badge-info">${Number(task.workload).toFixed(1)}</span></td>
        <td><span class="badge ${typeBadge}">${escapeHtml(task.type || 'General')}</span></td>
        <td>${escapeHtml(task.clusterId || 'Cluster-A')}</td>
        <td>
          <span class="badge badge-green" style="font-weight: 700;">
            ${escapeHtml(task.assignedNodeId || '--')}
          </span>
        </td>
        <td style="color: var(--text-muted); font-size: 0.8rem;">${assignedTime}</td>
        <td style="text-align: right;">
          <button class="btn btn-sm btn-outline btn-inspect-trace" data-task-id="${task.taskId}">
            🔍 Trace
          </button>
        </td>
      `;

      const btn = tr.querySelector('.btn-inspect-trace');
      if (btn) {
        btn.addEventListener('click', () => inspectTaskTrace(task.taskId));
      }

      assignedTbody.appendChild(tr);
    });
  }

  function getTaskTypeBadge(type) {
    const t = (type || '').toLowerCase();
    if (t.includes('search')) return 'badge-task-search';
    if (t.includes('medical')) return 'badge-task-medical';
    if (t.includes('food')) return 'badge-task-food';
    return 'badge-task-general';
  }

  function updateKpis() {
    // Nodes
    const totalNodes = candidateNodes.length;
    const onlineNodes = candidateNodes.filter(n => n.available !== false && (n.status || '').toUpperCase() !== 'UNAVAILABLE').length;
    if (kpiActiveNodes) kpiActiveNodes.textContent = `${onlineNodes} / ${totalNodes}`;
    if (kpiNodesBadge) {
      if (onlineNodes === totalNodes && totalNodes > 0) {
        kpiNodesBadge.className = 'badge badge-green';
        kpiNodesBadge.textContent = 'ALL ONLINE';
      } else if (onlineNodes > 0) {
        kpiNodesBadge.className = 'badge badge-warning';
        kpiNodesBadge.textContent = 'DEGRADED';
      } else {
        kpiNodesBadge.className = 'badge badge-red';
        kpiNodesBadge.textContent = 'OFFLINE';
      }
    }

    // Pending
    if (kpiPendingTasks) kpiPendingTasks.textContent = pendingTasks.length;
    const pendingWork = pendingTasks.reduce((acc, t) => acc + (Number(t.workload) || 0), 0);
    if (kpiPendingWorkload) kpiPendingWorkload.textContent = pendingWork.toFixed(1);

    // Assigned
    if (kpiAssignedTasks) kpiAssignedTasks.textContent = assignedTasks.length;
    const assignedWork = assignedTasks.reduce((acc, t) => acc + (Number(t.workload) || 0), 0);
    if (kpiAssignedWorkload) kpiAssignedWorkload.textContent = assignedWork.toFixed(1);

    // Fleet utilization
    let totalUtil = 0;
    let counted = 0;
    candidateNodes.forEach(n => {
      let u = n.utilization;
      if (u == null || isNaN(u) || !isFinite(u)) {
        const eff = n.effectiveCapacity || 0;
        const load = n.currentLoad || n.load || 0;
        u = eff > 0 ? (load / eff) : 0;
      }
      totalUtil += u;
      counted++;
    });
    const avgUtil = counted > 0 ? (totalUtil / counted) * 100 : 0;
    if (kpiAvgUtilization) kpiAvgUtilization.textContent = `${avgUtil.toFixed(1)}%`;
  }

  // ── Actions ──

  async function assignSingleTask(taskId) {
    try {
      const result = await window.api.post(`/api/tasks/${encodeURIComponent(taskId)}/assign`);
      handleAssignmentSuccess(result);
    } catch (err) {
      console.error(`Failed to assign task ${taskId}:`, err);
      if (window.toast) window.toast.error('Assignment Error', `Could not assign task ${taskId}: ${err.message}`);
    }
  }

  async function assignAllPending() {
    if (pendingTasks.length === 0) {
      if (window.toast) window.toast.info('No Tasks', 'No pending tasks to assign. Click 🌱 Load Sample Tasks first.');
      return;
    }

    try {
      const results = await window.api.post('/api/loadbalancer/assign-all');
      if (Array.isArray(results) && results.length > 0) {
        const last = results[results.length - 1];
        handleAssignmentSuccess(last);
        if (window.toast) {
          window.toast.success('Batch Complete', `Successfully balanced & assigned ${results.length} tasks across candidate fleet.`);
        }
      } else {
        await refreshAll();
      }
    } catch (err) {
      console.error('Failed to assign all tasks:', err);
      if (window.toast) window.toast.error('Assign-All Failed', err.message);
    }
  }

  function handleAssignmentSuccess(result) {
    if (!result) return;
    latestResult = result;
    if (result.taskId) {
      tracesByTaskId[result.taskId] = result;
    }

    // Highlight node card
    if (result.chosenNodeId) {
      const card = document.getElementById(`node-card-${result.chosenNodeId}`);
      if (card) {
        card.classList.remove('highlight-assigned');
        void card.offsetWidth; // retrigger CSS animation
        card.classList.add('highlight-assigned');
      }
    }

    renderTraceInspector(result);
    refreshAll();

    if (window.toast && result.success) {
      window.toast.success(
        'Task Assigned',
        `${result.taskId} -> ${result.chosenNodeId} (Load: ${result.updatedLoad != null ? result.updatedLoad.toFixed(2) : '--'}, Util: ${(result.finalUtilization != null ? (result.finalUtilization * 100).toFixed(1) : '--')}%)`
      );
    }
  }

  function inspectTaskTrace(taskId) {
    const trace = tracesByTaskId[taskId];
    if (trace) {
      renderTraceInspector(trace);
      if (window.toast) window.toast.info('Trace Loaded', `Displaying assignment decision steps for ${taskId}`);
    } else {
      if (window.toast) window.toast.warning('Trace', `Detailed trace memory expired for ${taskId}.`);
    }
  }

  function renderTraceInspector(result) {
    if (!result) return;

    if (traceMetaBadge) {
      if (result.success) {
        traceMetaBadge.innerHTML = `<span class="badge badge-green">ASSIGNED TO ${escapeHtml(result.chosenNodeId)}</span>`;
      } else {
        traceMetaBadge.innerHTML = `<span class="badge badge-red">FAILED / NO NODE</span>`;
      }
    }

    if (traceSummary) {
      traceSummary.innerHTML = `
        <strong>Task:</strong> ${escapeHtml(result.taskId || '--')} &nbsp;|&nbsp;
        <strong>Workload:</strong> ${result.workload != null ? result.workload.toFixed(1) : '--'} &nbsp;|&nbsp;
        <strong>Winner:</strong> <span style="color: #38bdf8; font-weight:700;">${escapeHtml(result.chosenNodeId || 'None')}</span> &nbsp;|&nbsp;
        <strong>Updated Load:</strong> ${result.updatedLoad != null ? result.updatedLoad.toFixed(2) : '--'}
      `;
    }

    if (traceTerminal) {
      traceTerminal.innerHTML = '';
      const steps = result.steps || [];
      if (steps.length === 0) {
        traceTerminal.innerHTML = `<div class="trace-step-line" style="color:#64748b;">// No steps reported.</div>`;
      } else {
        steps.forEach(step => {
          const line = document.createElement('div');
          line.className = 'trace-step-line';
          if (step.includes('ASSIGNED SUCCESSFULLY') || step.includes('TASK ASSIGNED')) {
            line.className += ' trace-success';
          } else if (step.includes('Projected Utilization =')) {
            line.className += ' trace-highlight';
          } else if (step.includes('unavailable') || step.includes('Skipping') || step.includes('No available')) {
            line.className += ' trace-warning';
          }
          line.textContent = step;
          traceTerminal.appendChild(line);
        });
      }
    }

    // Evaluations Breakdown Table
    if (evalBreakdownWrap && evalTableBody) {
      const evals = result.evaluations || [];
      if (evals.length > 0) {
        evalBreakdownWrap.style.display = 'block';
        evalTableBody.innerHTML = '';
        evals.forEach(ev => {
          const tr = document.createElement('tr');
          const isWinner = ev.nodeId === result.chosenNodeId;
          const eff = ev.effectiveCapacity != null ? ev.effectiveCapacity.toFixed(2) : '--';
          const cur = ev.currentLoad != null ? ev.currentLoad.toFixed(2) : '0.00';
          const proj = ev.projectedUtilization != null ? ev.projectedUtilization.toFixed(2) : '--';

          let outcomeHtml = '';
          if (isWinner) {
            outcomeHtml = `<span class="badge badge-green" style="font-size:0.7rem;">SELECTED (Lowest: ${proj})</span>`;
          } else if (ev.skipped) {
            outcomeHtml = `<span class="badge badge-red" style="font-size:0.7rem;" title="${escapeHtml(ev.skippedReason || 'Skipped')}">SKIPPED</span>`;
          } else {
            outcomeHtml = `<span class="badge badge-info" style="font-size:0.7rem;">EVALUATED (${proj})</span>`;
          }

          tr.innerHTML = `
            <td><strong>${escapeHtml(ev.nodeId)}</strong></td>
            <td>${eff}</td>
            <td>${cur}</td>
            <td>${proj}</td>
            <td>${outcomeHtml}</td>
          `;
          evalTableBody.appendChild(tr);
        });
      } else {
        evalBreakdownWrap.style.display = 'none';
      }
    }
  }

  async function toggleNodeAvailability(nodeId, newAvailable) {
    try {
      const updated = await window.api.post(`/api/loadbalancer/nodes/${encodeURIComponent(nodeId)}/available?available=${newAvailable}`);
      if (window.toast) {
        if (newAvailable) {
          window.toast.success('Node Online', `${nodeId} is now AVAILABLE for task distribution.`);
        } else {
          window.toast.warning('Node Unavailable', `${nodeId} marked OFFLINE. The load balancer will skip it on future assignments.`);
        }
      }
      await refreshNodes();
    } catch (err) {
      console.error(`Failed to toggle availability for ${nodeId}:`, err);
      // Fallback for mock mode
      const n = candidateNodes.find(x => x.nodeId === nodeId);
      if (n) {
        n.available = newAvailable;
        n.status = newAvailable ? 'AVAILABLE' : 'UNAVAILABLE';
        renderNodes();
        updateKpis();
        if (window.toast) window.toast.info('Node Status (Mock)', `${nodeId} set to ${n.status}`);
      }
    }
  }

  async function seedSampleTasks() {
    try {
      const seeded = await window.api.post('/api/tasks/seed');
      if (window.toast) {
        window.toast.success('Sample Tasks Loaded', `Seeded ${Array.isArray(seeded) ? seeded.length : '8'} disaster relief tasks into the queue.`);
      }
      await refreshTasks();
    } catch (err) {
      console.error('Failed to seed tasks:', err);
      // Mock mode fallback
      if (window.api && window.api.isMock()) {
        pendingTasks = [
          { taskId: 'TASK-01', description: 'Search for survivors in Sector 1', workload: 3.0, type: 'Search', clusterId: 'Cluster-A', status: 'PENDING' },
          { taskId: 'TASK-02', description: 'Inspect damaged school building', workload: 2.0, type: 'Search', clusterId: 'Cluster-A', status: 'PENDING' },
          { taskId: 'TASK-03', description: 'Deliver emergency medical supplies', workload: 4.0, type: 'Medical', clusterId: 'Cluster-B', status: 'PENDING' },
          { taskId: 'TASK-04', description: 'Air-drop ration packets to relief camp', workload: 2.0, type: 'Food', clusterId: 'Cluster-A', status: 'PENDING' },
          { taskId: 'TASK-05', description: 'Relay emergency radio comms', workload: 3.0, type: 'Search', clusterId: 'Cluster-B', status: 'PENDING' },
          { taskId: 'TASK-06', description: 'Thermal scan of collapsed warehouse', workload: 2.0, type: 'Search', clusterId: 'Cluster-C', status: 'PENDING' },
          { taskId: 'TASK-07', description: 'Transport trauma triage kit', workload: 5.0, type: 'Medical', clusterId: 'Cluster-A', status: 'PENDING' },
          { taskId: 'TASK-08', description: 'Water purification supply delivery', workload: 3.0, type: 'Food', clusterId: 'Cluster-B', status: 'PENDING' }
        ];
        renderTasks();
        updateKpis();
        if (window.toast) window.toast.success('Sample Tasks (Mock)', 'Seeded 8 legacy tasks.');
      }
    }
  }

  async function resetSimulation() {
    try {
      await window.api.post('/api/loadbalancer/reset');
      if (window.toast) window.toast.info('Simulation Reset', 'Candidate node loads reset to 0.0 and all nodes set online.');
      await refreshAll();
    } catch (err) {
      console.error('Failed to reset load balancer:', err);
      // Fallback
      candidateNodes.forEach(n => {
        n.currentLoad = 0.0;
        n.load = 0.0;
        n.utilization = 0.0;
        n.available = true;
        n.status = 'AVAILABLE';
      });
      renderNodes();
      updateKpis();
    }
  }

  async function clearAllTasks() {
    try {
      await window.api.del('/api/tasks');
      if (window.toast) window.toast.info('Tasks Cleared', 'All rescue tasks removed from database.');
      pendingTasks = [];
      assignedTasks = [];
      renderTasks();
      updateKpis();
    } catch (err) {
      console.error('Failed to clear tasks:', err);
    }
  }

  function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

})();
