/**
 * analytics.js - Finished Task Pipeline & Distributed MapReduce Analytics (Day 4)
 * Author: Parth (Compute & Analytics Backend)
 */

(function () {
  'use strict';

  // State
  let allTasks = [];
  let filteredTasks = [];
  let currentPage = 1;
  let pageSize = 10;
  let analyticsResult = null;

  // DOM Elements - Action Buttons
  const btnRun = document.getElementById('btn-run-analytics');
  const runBtnText = document.getElementById('run-btn-text');
  const runSpinner = document.getElementById('run-spinner');
  const btnRunPipeline = document.getElementById('btn-run-pipeline');
  const btnSeed = document.getElementById('btn-seed-tasks');
  const btnReset = document.getElementById('btn-reset-tasks');
  const btnRefreshHistory = document.getElementById('btn-refresh-history');
  const btnClearTrace = document.getElementById('btn-clear-trace');
  const activeEngineBadge = document.getElementById('active-engine-badge');
  const traceTerminal = document.getElementById('trace-terminal');

  // KPI Elements
  const kpiTotalTasks = document.getElementById('kpi-total-tasks');
  const kpiAssignedBadge = document.getElementById('kpi-assigned-badge');
  const kpiPendingSub = document.getElementById('kpi-pending-sub');
  const kpiTotalWorkload = document.getElementById('kpi-total-workload');
  const kpiBusiestCluster = document.getElementById('kpi-busiest-cluster');
  const kpiBusiestSub = document.getElementById('kpi-busiest-sub');
  const kpiDuration = document.getElementById('kpi-duration');
  const kpiEngineMode = document.getElementById('kpi-engine-mode');
  const sourceFileLabel = document.getElementById('source-file-label');

  // Filters Elements
  const filterCluster = document.getElementById('filter-cluster');
  const filterType = document.getElementById('filter-type');
  const filterStatus = document.getElementById('filter-status');
  const filterSearch = document.getElementById('filter-search');
  const btnResetFilters = document.getElementById('btn-reset-filters');

  // Pagination Elements
  const pageSizeSelect = document.getElementById('page-size-select');
  const btnPrevPage = document.getElementById('btn-prev-page');
  const btnNextPage = document.getElementById('btn-next-page');
  const pageNumDisplay = document.getElementById('page-num-display');
  const paginationInfo = document.getElementById('pagination-info');

  // Tables
  const taskTableBody = document.getElementById('task-table-body');
  const clusterTableBody = document.getElementById('cluster-table-body');
  const typeTableBody = document.getElementById('type-table-body');
  const breakdownTableBody = document.getElementById('breakdown-table-body');
  const historyTableBody = document.getElementById('history-table-body');

  // Chart Instances
  let analyticsChart = null;
  let timelineChart = null;

  // Initialize
  document.addEventListener('DOMContentLoaded', () => {
    initButtons();
    initFilters();
    initPagination();
    initCharts();
    initEvents();
    loadAll();
  });

  function initButtons() {
    if (btnRun) {
      btnRun.addEventListener('click', () => {
        const selectedRadio = document.querySelector('input[name="engineSelect"]:checked');
        const engine = selectedRadio ? selectedRadio.value : 'streams';
        runMapReduce(engine);
      });
    }

    if (btnRunPipeline) {
      btnRunPipeline.addEventListener('click', runPartitionedPipeline);
    }

    if (btnSeed) {
      btnSeed.addEventListener('click', seedSampleTasks);
    }

    if (btnReset) {
      btnReset.addEventListener('click', resetAllTasks);
    }

    if (btnRefreshHistory) {
      btnRefreshHistory.addEventListener('click', loadHistory);
    }

    if (btnClearTrace) {
      btnClearTrace.addEventListener('click', () => {
        if (traceTerminal) traceTerminal.textContent = '[TRACE CLEARED] Ready for next pipeline or MapReduce execution.\n';
      });
    }
  }

  function initFilters() {
    const handleFilterChange = () => {
      currentPage = 1;
      applyFilters();
    };

    if (filterCluster) filterCluster.addEventListener('change', handleFilterChange);
    if (filterType) filterType.addEventListener('change', handleFilterChange);
    if (filterStatus) filterStatus.addEventListener('change', handleFilterChange);
    if (filterSearch) filterSearch.addEventListener('input', handleFilterChange);

    if (btnResetFilters) {
      btnResetFilters.addEventListener('click', () => {
        if (filterCluster) filterCluster.value = '';
        if (filterType) filterType.value = '';
        if (filterStatus) filterStatus.value = '';
        if (filterSearch) filterSearch.value = '';
        currentPage = 1;
        applyFilters();
      });
    }
  }

  function initPagination() {
    if (pageSizeSelect) {
      pageSizeSelect.addEventListener('change', (e) => {
        pageSize = parseInt(e.target.value, 10) || 10;
        currentPage = 1;
        renderPaginatedTasks();
      });
    }

    if (btnPrevPage) {
      btnPrevPage.addEventListener('click', () => {
        if (currentPage > 1) {
          currentPage--;
          renderPaginatedTasks();
        }
      });
    }

    if (btnNextPage) {
      btnNextPage.addEventListener('click', () => {
        const totalPages = Math.ceil(filteredTasks.length / pageSize) || 1;
        if (currentPage < totalPages) {
          currentPage++;
          renderPaginatedTasks();
        }
      });
    }
  }

  function initEvents() {
    if (window.events && typeof window.events.createLiveLog === 'function') {
      window.events.createLiveLog('live-event-log', { filterModule: 'compute', maxEntries: 50 });
    }

    if (window.events && typeof window.events.on === 'function') {
      window.events.on('compute', 'TASK_CREATED', (payload) => {
        appendTrace(`[EVENT: TASK_CREATED] ${payload.taskId || 'Task'} created in ${payload.clusterId || 'Cluster'}`);
        loadTasks();
      });

      window.events.on('compute', 'TASK_ASSIGNED', (payload) => {
        const leader = payload.assignedByLeaderId || payload.leaderId;
        const node = payload.assignedNodeId || payload.chosenNodeId;
        const leaderInfo = leader ? ` via Leader [${leader}]` : '';
        appendTrace(`[EVENT: TASK_ASSIGNED] Task ${payload.taskId} assigned to [${node}]${leaderInfo} in ${payload.clusterId || 'Cluster'}`);
        loadTasks();
      });

      window.events.on('compute', 'ANALYTICS_STARTED', (payload) => {
        appendTrace(`[EVENT: ANALYTICS_STARTED] Engine: ${(payload.engine || 'unknown').toUpperCase()} | Tasks: ${payload.taskCount || '--'}`);
      });

      window.events.on('compute', 'ANALYTICS_DONE', (payload) => {
        appendTrace(`[EVENT: ANALYTICS_DONE] Aggregated ${payload.totalTasks || 0} tasks (${payload.totalWorkload || 0} workload) in ${payload.durationMs || 0} ms`);
        loadHistory();
      });
    }
  }

  function initCharts() {
    if (typeof Chart === 'undefined') return;

    // 1. Cluster Workload & Task Count Bar Chart
    const barCanvas = document.getElementById('analyticsChart');
    if (barCanvas) {
      const ctx = barCanvas.getContext('2d');
      analyticsChart = new Chart(ctx, {
        type: 'bar',
        data: {
          labels: [],
          datasets: [
            {
              label: 'Total Workload',
              data: [],
              backgroundColor: 'rgba(59, 130, 246, 0.75)',
              borderColor: 'rgb(59, 130, 246)',
              borderWidth: 1.5,
              borderRadius: 4,
              yAxisID: 'y'
            },
            {
              label: 'Task Count',
              data: [],
              backgroundColor: 'rgba(16, 185, 129, 0.75)',
              borderColor: 'rgb(16, 185, 129)',
              borderWidth: 1.5,
              borderRadius: 4,
              yAxisID: 'y1'
            }
          ]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          interaction: { mode: 'index', intersect: false },
          scales: {
            y: {
              type: 'linear',
              display: true,
              position: 'left',
              title: { display: true, text: 'Workload (Units)', font: { size: 11, weight: '600' } },
              beginAtZero: true
            },
            y1: {
              type: 'linear',
              display: true,
              position: 'right',
              title: { display: true, text: 'Task Count (#)', font: { size: 11, weight: '600' } },
              grid: { drawOnChartArea: false },
              beginAtZero: true,
              ticks: { precision: 0 }
            }
          },
          plugins: {
            legend: { position: 'top', labels: { boxWidth: 12, font: { size: 12 } } },
            tooltip: { padding: 10, cornerRadius: 6 }
          }
        }
      });
    }

    // 2. Tasks-Over-Time Timeline Chart
    const lineCanvas = document.getElementById('timelineChart');
    if (lineCanvas) {
      const ctx = lineCanvas.getContext('2d');
      timelineChart = new Chart(ctx, {
        type: 'line',
        data: {
          labels: [],
          datasets: [
            {
              label: 'Cumulative Workload',
              data: [],
              borderColor: '#7c3aed',
              backgroundColor: 'rgba(124, 58, 237, 0.1)',
              fill: true,
              tension: 0.35,
              borderWidth: 2,
              yAxisID: 'y'
            },
            {
              label: 'Cumulative Tasks',
              data: [],
              borderColor: '#0284c7',
              backgroundColor: 'rgba(2, 132, 199, 0.1)',
              fill: false,
              tension: 0.35,
              borderWidth: 2,
              borderDash: [4, 4],
              yAxisID: 'y1'
            }
          ]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          interaction: { mode: 'index', intersect: false },
          scales: {
            x: {
              title: { display: true, text: 'Chronological Task Sequence', font: { size: 11, weight: '600' } }
            },
            y: {
              type: 'linear',
              display: true,
              position: 'left',
              title: { display: true, text: 'Cum. Workload', font: { size: 11, weight: '600' } },
              beginAtZero: true
            },
            y1: {
              type: 'linear',
              display: true,
              position: 'right',
              title: { display: true, text: 'Cum. Tasks', font: { size: 11, weight: '600' } },
              grid: { drawOnChartArea: false },
              beginAtZero: true,
              ticks: { precision: 0 }
            }
          },
          plugins: {
            legend: { position: 'top', labels: { boxWidth: 12, font: { size: 12 } } },
            tooltip: { padding: 10, cornerRadius: 6 }
          }
        }
      });
    }
  }

  async function loadAll() {
    await loadTasks();
    await loadLatestAnalytics();
    await loadHistory();
  }

  async function loadTasks() {
    try {
      let data;
      if (window.api && window.api.isMock()) {
        data = getMockTasks();
      } else {
        data = await window.api.get('/api/tasks');
      }

      allTasks = Array.isArray(data) ? data : [];
      applyFilters();
      updateTimelineChart(allTasks);
      calculateKPIsFromTasks(allTasks);

    } catch (err) {
      console.warn('Failed to load tasks:', err);
    }
  }

  function applyFilters() {
    const clusterVal = (filterCluster ? filterCluster.value : '').toLowerCase();
    const typeVal = (filterType ? filterType.value : '').toLowerCase();
    const statusVal = (filterStatus ? filterStatus.value : '').toLowerCase();
    const searchVal = (filterSearch ? filterSearch.value : '').trim().toLowerCase();

    filteredTasks = allTasks.filter(t => {
      const matchCluster = !clusterVal || (t.clusterId || '').toLowerCase() === clusterVal;
      const matchType = !typeVal || (t.type || '').toLowerCase() === typeVal;
      const matchStatus = !statusVal || (t.status || '').toLowerCase() === statusVal;
      const matchSearch = !searchVal ||
        (t.taskId || '').toLowerCase().includes(searchVal) ||
        (t.description || '').toLowerCase().includes(searchVal) ||
        (t.assignedNodeId || '').toLowerCase().includes(searchVal) ||
        (t.assignedByLeaderId || '').toLowerCase().includes(searchVal);

      return matchCluster && matchType && matchStatus && matchSearch;
    });

    renderPaginatedTasks();
  }

  function renderPaginatedTasks() {
    if (!taskTableBody) return;

    const total = filteredTasks.length;
    const totalPages = Math.ceil(total / pageSize) || 1;
    if (currentPage > totalPages) currentPage = totalPages;
    if (currentPage < 1) currentPage = 1;

    const startIndex = (currentPage - 1) * pageSize;
    const endIndex = Math.min(startIndex + pageSize, total);
    const pageItems = filteredTasks.slice(startIndex, endIndex);

    // Update pagination controls
    if (pageNumDisplay) pageNumDisplay.textContent = `${currentPage} / ${totalPages}`;
    if (paginationInfo) {
      paginationInfo.textContent = total > 0
        ? `Showing ${startIndex + 1}-${endIndex} of ${total} tasks`
        : 'Showing 0-0 of 0 tasks';
    }
    if (btnPrevPage) btnPrevPage.disabled = (currentPage <= 1);
    if (btnNextPage) btnNextPage.disabled = (currentPage >= totalPages);

    if (pageItems.length === 0) {
      taskTableBody.innerHTML = '<tr><td colspan="9" class="text-center text-muted">No rescue tasks match criteria</td></tr>';
      return;
    }

    taskTableBody.innerHTML = pageItems.map(task => {
      const isAssigned = (task.status || '').toUpperCase() === 'ASSIGNED';
      const statusBadge = isAssigned
        ? '<span class="badge badge-green">ASSIGNED</span>'
        : '<span class="badge badge-amber">PENDING</span>';

      let typeBadge = 'badge-blue';
      const t = (task.type || '').toLowerCase();
      if (t === 'medical') typeBadge = 'badge-red';
      else if (t === 'food') typeBadge = 'badge-amber';
      else if (t === 'search') typeBadge = 'badge-green';

      // Node link to load balancer
      const nodeHtml = task.assignedNodeId
        ? `<a href="/pages/loadbalancer.html?node=${encodeURIComponent(task.assignedNodeId)}" class="node-link-pill" title="Inspect node on Load Balancer">
             <span>${(task.assignedNodeId.toLowerCase().includes('edge') ? '🖥️' : '🛸')}</span>
             <span>${escapeHtml(task.assignedNodeId)}</span>
           </a>`
        : '<span style="color: #94a3b8; font-size: 0.8rem;">--</span>';

      // Leader badge
      const leaderHtml = task.assignedByLeaderId
        ? `<span class="badge badge-leader" title="Assigned by elected cluster leader">👑 ${escapeHtml(task.assignedByLeaderId)}</span>`
        : '<span style="color: #94a3b8; font-size: 0.8rem;">--</span>';

      const timeStr = task.assignedAt
        ? new Date(task.assignedAt).toLocaleTimeString()
        : (task.createdAt ? new Date(task.createdAt).toLocaleTimeString() : '--');

      return `
        <tr>
          <td><strong>${escapeHtml(task.taskId)}</strong></td>
          <td style="max-width: 220px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="${escapeHtml(task.description)}">
            ${escapeHtml(task.description)}
          </td>
          <td><span class="badge ${typeBadge}">${escapeHtml(task.type || 'General')}</span></td>
          <td><strong>${Number(task.workload || 0).toFixed(1)}</strong></td>
          <td><span style="color: #475569; font-weight: 500;">${escapeHtml(task.clusterId || 'Cluster-A')}</span></td>
          <td>${statusBadge}</td>
          <td>${nodeHtml}</td>
          <td>${leaderHtml}</td>
          <td style="color: #64748b; font-size: 0.8rem;">${timeStr}</td>
        </tr>
      `;
    }).join('');
  }

  function calculateKPIsFromTasks(tasks) {
    const total = tasks.length;
    const assignedCount = tasks.filter(t => (t.status || '').toUpperCase() === 'ASSIGNED').length;
    const pendingCount = total - assignedCount;
    const totalWorkload = tasks.reduce((sum, t) => sum + (Number(t.workload) || 0), 0);

    if (kpiTotalTasks) kpiTotalTasks.textContent = total;
    if (kpiAssignedBadge) kpiAssignedBadge.textContent = `${assignedCount} ASSIGNED`;
    if (kpiPendingSub) kpiPendingSub.textContent = `${pendingCount} pending`;
    if (kpiTotalWorkload) kpiTotalWorkload.textContent = totalWorkload.toFixed(1);

    // Calculate Busiest Cluster
    const clusterWorkloads = {};
    const clusterCounts = {};
    tasks.forEach(t => {
      const c = t.clusterId || 'Cluster-A';
      clusterWorkloads[c] = (clusterWorkloads[c] || 0) + (Number(t.workload) || 0);
      clusterCounts[c] = (clusterCounts[c] || 0) + 1;
    });

    let busiest = null;
    let maxWorkload = -1;
    Object.keys(clusterWorkloads).forEach(c => {
      if (clusterWorkloads[c] > maxWorkload) {
        maxWorkload = clusterWorkloads[c];
        busiest = c;
      }
    });

    if (kpiBusiestCluster) {
      if (busiest) {
        kpiBusiestCluster.textContent = busiest;
        if (kpiBusiestSub) {
          kpiBusiestSub.textContent = `${maxWorkload.toFixed(1)} workload (${clusterCounts[busiest] || 0} tasks)`;
        }
      } else {
        kpiBusiestCluster.textContent = '--';
        if (kpiBusiestSub) kpiBusiestSub.textContent = 'No active tasks';
      }
    }
  }

  function updateTimelineChart(tasks) {
    if (!timelineChart || !Array.isArray(tasks) || tasks.length === 0) return;

    // Sort chronologically
    const sorted = [...tasks].sort((a, b) => {
      const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      return ta - tb;
    });

    const labels = [];
    const cumulativeWorkloads = [];
    const cumulativeCounts = [];

    let cumW = 0;
    let cumC = 0;

    sorted.forEach((task, index) => {
      cumW += (Number(task.workload) || 0);
      cumC += 1;
      labels.push(task.taskId || `#${index + 1}`);
      cumulativeWorkloads.push(parseFloat(cumW.toFixed(1)));
      cumulativeCounts.push(cumC);
    });

    timelineChart.data.labels = labels;
    timelineChart.data.datasets[0].data = cumulativeWorkloads;
    timelineChart.data.datasets[1].data = cumulativeCounts;
    timelineChart.update();
  }

  async function runPartitionedPipeline() {
    setPipelineLoading(true);
    appendTrace('\n======================================================================');
    appendTrace('>>> [SCENARIO PIPELINE] EXECUTING CLUSTER-PARTITIONED TASK ALLOCATION');
    appendTrace('======================================================================');

    try {
      let results;
      if (window.api && window.api.isMock()) {
        results = getMockPipelineResults();
      } else {
        results = await window.api.post('/api/tasks/pipeline/partitioned');
      }

      if (Array.isArray(results)) {
        results.forEach(res => {
          const leader = res.assignedByLeaderId || 'LEADER';
          const node = res.chosenNodeId || 'NODE';
          appendTrace(`  -> Cluster [${res.clusterId || 'Sector'}] Leader [${leader}] assigned ${res.taskId} (${res.workload} units) to [${node}]`);
          if (Array.isArray(res.steps)) {
            res.steps.forEach(s => appendTrace(`     ${s}`));
          }
        });
      }

      appendTrace('[SCENARIO PIPELINE] Task dispatch across partition boundaries finished.');
      appendTrace('[SCENARIO PIPELINE] Healing clusters and triggering full MapReduce analytics...');

      if (window.toast) {
        window.toast.success(
          'Pipeline Executed',
          `Dispatched ${Array.isArray(results) ? results.length : 6} partitioned tasks via elected leaders.`
        );
      }

      // Refresh task directory and auto-run MapReduce to reflect updated telemetry
      await loadTasks();
      const selectedRadio = document.querySelector('input[name="engineSelect"]:checked');
      const engine = selectedRadio ? selectedRadio.value : 'streams';
      await runMapReduce(engine);

    } catch (err) {
      console.error('Pipeline execution failed:', err);
      appendTrace(`[ERROR] Partitioned pipeline failed: ${err.message || err}`);
      if (window.toast) {
        window.toast.error('Pipeline Error', err.message || 'Failed to dispatch partitioned tasks');
      }
    } finally {
      setPipelineLoading(false);
    }
  }

  async function runMapReduce(engine) {
    setLoading(true);
    appendTrace(`\n======================================================================`);
    appendTrace(`>>> DISPATCHING MAPREDUCE ANALYTICS (Engine: ${engine.toUpperCase()})`);
    appendTrace(`======================================================================`);

    try {
      let result;
      if (window.api && window.api.isMock()) {
        result = await window.api.get('/mock/analytics_latest.json');
        result.engine = engine;
      } else {
        result = await window.api.post(`/api/analytics/mapreduce/run?engine=${encodeURIComponent(engine)}`, { engine });
      }

      analyticsResult = result;
      renderAnalyticsResult(result);

      if (window.toast) {
        const engineLabel = (result.engine || '').toUpperCase();
        window.toast.success(
          'MapReduce Completed',
          `Processed ${result.totalTasks} tasks in ${result.durationMs} ms using ${engineLabel}.`
        );
      }
      loadHistory();

    } catch (err) {
      console.error('MapReduce run failed:', err);
      appendTrace(`[ERROR] MapReduce failed: ${err.message || err}`);
      if (window.toast) {
        window.toast.error('Analytics Error', err.message || 'Failed to complete MapReduce run');
      }
    } finally {
      setLoading(false);
    }
  }

  async function seedSampleTasks() {
    try {
      if (window.api && window.api.isMock()) {
        if (window.toast) window.toast.success('Tasks Seeded', 'Seeded sample disaster relief tasks (Mock).');
        await loadTasks();
        await runMapReduce('streams');
        return;
      }

      const res = await window.api.post('/api/tasks/seed');
      if (window.toast) {
        window.toast.success('Tasks Seeded', `Loaded ${Array.isArray(res) ? res.length : 8} disaster relief tasks.`);
      }
      await loadTasks();
      const selectedRadio = document.querySelector('input[name="engineSelect"]:checked');
      const engine = selectedRadio ? selectedRadio.value : 'streams';
      await runMapReduce(engine);
    } catch (err) {
      console.error('Failed to seed tasks:', err);
      if (window.toast) window.toast.error('Seed Error', err.message || 'Could not seed tasks');
    }
  }

  async function resetAllTasks() {
    if (!confirm('Are you sure you want to clear all tasks and reset node loads?')) return;

    try {
      if (window.api && window.api.isMock()) {
        allTasks = [];
        applyFilters();
        if (window.toast) window.toast.info('Tasks Reset', 'All tasks cleared (Mock).');
        return;
      }

      const res = await window.api.del('/api/tasks');
      if (window.toast) {
        window.toast.info('Tasks Reset', res.message || 'All rescue tasks cleared.');
      }
      appendTrace('[RESET] Deleted all rescue tasks and reset candidate node loads.');
      await loadTasks();
      await runMapReduce('streams');
    } catch (err) {
      console.error('Failed to reset tasks:', err);
      if (window.toast) window.toast.error('Reset Error', err.message || 'Could not reset tasks');
    }
  }

  async function loadLatestAnalytics() {
    try {
      let result;
      if (window.api && window.api.isMock()) {
        result = await window.api.get('/mock/analytics_latest.json');
      } else {
        result = await window.api.get('/api/analytics/mapreduce/latest');
      }
      if (result) {
        analyticsResult = result;
        renderAnalyticsResult(result);
      }
    } catch (err) {
      console.warn('No prior analytics run:', err);
    }
  }

  async function loadHistory() {
    try {
      let history;
      if (window.api && window.api.isMock()) {
        history = await window.api.get('/mock/analytics_history.json');
      } else {
        history = await window.api.get('/api/analytics/mapreduce/history');
      }
      renderHistoryTable(history);
    } catch (err) {
      console.warn('Failed to fetch analytics history:', err);
    }
  }

  function renderAnalyticsResult(res) {
    if (!res) return;

    // 1. Update Engine Badge
    const engine = (res.engine || 'streams').toLowerCase();
    if (activeEngineBadge) {
      if (engine === 'spark') {
        activeEngineBadge.className = 'badge engine-badge-spark';
        activeEngineBadge.textContent = 'ENGINE: APACHE SPARK';
      } else if (res.fallbackUsed || engine.includes('fallback')) {
        activeEngineBadge.className = 'badge engine-badge-fallback';
        activeEngineBadge.textContent = 'ENGINE: STREAMS (FALLBACK)';
      } else {
        activeEngineBadge.className = 'badge engine-badge-streams';
        activeEngineBadge.textContent = 'ENGINE: JAVA STREAMS';
      }
    }

    if (kpiDuration) kpiDuration.textContent = `${res.durationMs != null ? res.durationMs : 0} ms`;
    if (kpiEngineMode) kpiEngineMode.textContent = (res.engine || 'streams').toUpperCase();
    if (sourceFileLabel && res.inputFile) sourceFileLabel.textContent = res.inputFile;

    // 2. Render Marginal Tables
    renderClusterTable(res.countsPerCluster || {}, res.workloadPerCluster || {}, res.totalWorkload || 0);
    renderTypeTable(res.countsPerType || {}, res.workloadPerType || {});

    // 3. Render MapReduce Key Breakdown Table
    renderBreakdownTable(res.breakdown || []);

    // 4. Update Bar Chart
    updateBarChart(res.countsPerCluster || {}, res.workloadPerCluster || {});

    // 5. Output Steps to Terminal
    if (Array.isArray(res.steps) && res.steps.length > 0) {
      res.steps.forEach(step => appendTrace(step));
    }
  }

  function renderClusterTable(counts, workloads, totalWorkload) {
    if (!clusterTableBody) return;
    const clusters = Object.keys(workloads);

    if (clusters.length === 0) {
      clusterTableBody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No cluster data</td></tr>';
      return;
    }

    clusterTableBody.innerHTML = clusters.map(cluster => {
      const count = counts[cluster] || 0;
      const workload = workloads[cluster] || 0.0;
      const sharePct = totalWorkload > 0 ? ((workload / totalWorkload) * 100).toFixed(1) : 0;

      return `
        <tr>
          <td><strong style="color: #1e293b;">${escapeHtml(cluster)}</strong></td>
          <td><span class="badge badge-blue">${count} tasks</span></td>
          <td><strong>${workload.toFixed(1)}</strong></td>
          <td>
            <div style="display: flex; align-items: center; gap: 8px;">
              <div style="flex: 1; height: 6px; background: #e5e7eb; border-radius: 3px; overflow: hidden;">
                <div style="width: ${sharePct}%; height: 100%; background: #3b82f6;"></div>
              </div>
              <span style="font-size: 0.75rem; color: #64748b; min-width: 38px;">${sharePct}%</span>
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  function renderTypeTable(counts, workloads) {
    if (!typeTableBody) return;
    const types = Object.keys(counts);

    if (types.length === 0) {
      typeTableBody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No category data</td></tr>';
      return;
    }

    typeTableBody.innerHTML = types.map(type => {
      const count = counts[type] || 0;
      const workload = workloads[type] || 0.0;
      const avg = count > 0 ? (workload / count).toFixed(2) : '0.00';

      let typeBadge = 'badge-blue';
      if (type.toLowerCase() === 'medical') typeBadge = 'badge-red';
      else if (type.toLowerCase() === 'food') typeBadge = 'badge-amber';
      else if (type.toLowerCase() === 'search') typeBadge = 'badge-green';

      return `
        <tr>
          <td><span class="badge ${typeBadge}">${escapeHtml(type)}</span></td>
          <td>${count} tasks</td>
          <td><strong>${workload.toFixed(1)}</strong></td>
          <td style="color: #64748b;">${avg} / task</td>
        </tr>
      `;
    }).join('');
  }

  function renderBreakdownTable(breakdown) {
    if (!breakdownTableBody) return;

    if (!Array.isArray(breakdown) || breakdown.length === 0) {
      breakdownTableBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No MapReduce buckets</td></tr>';
      return;
    }

    breakdownTableBody.innerHTML = breakdown.map(item => {
      const compositeKey = `${item.cluster}|${item.taskType}`;
      return `
        <tr>
          <td><code>${escapeHtml(compositeKey)}</code></td>
          <td><strong>${escapeHtml(item.cluster)}</strong></td>
          <td><span class="badge badge-info">${escapeHtml(item.taskType)}</span></td>
          <td>${item.taskCount}</td>
          <td><strong style="color: #0369a1;">${Number(item.totalWorkload).toFixed(1)}</strong></td>
        </tr>
      `;
    }).join('');
  }

  function updateBarChart(counts, workloads) {
    if (!analyticsChart) return;

    const clusters = Object.keys(workloads);
    const workloadValues = clusters.map(c => workloads[c] || 0);
    const countValues = clusters.map(c => counts[c] || 0);

    analyticsChart.data.labels = clusters;
    analyticsChart.data.datasets[0].data = workloadValues;
    analyticsChart.data.datasets[1].data = countValues;
    analyticsChart.update();
  }

  function renderHistoryTable(runs) {
    if (!historyTableBody) return;

    if (!Array.isArray(runs) || runs.length === 0) {
      historyTableBody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">No historical runs</td></tr>';
      return;
    }

    historyTableBody.innerHTML = runs.map(run => {
      const timeStr = run.timestamp ? new Date(run.timestamp).toLocaleTimeString() : '--';
      const engine = (run.engine || 'unknown').toLowerCase();
      let engineBadge = 'badge-info';
      if (engine === 'spark') engineBadge = 'engine-badge-spark';
      else if (engine.includes('fallback')) engineBadge = 'engine-badge-fallback';
      else engineBadge = 'engine-badge-streams';

      return `
        <tr>
          <td>#${run.id != null ? run.id : '--'}</td>
          <td style="color: #64748b;">${escapeHtml(timeStr)}</td>
          <td><span class="badge ${engineBadge}">${escapeHtml(engine.toUpperCase())}</span></td>
          <td>${run.durationMs != null ? run.durationMs : 0} ms</td>
          <td>${run.totalTasks != null ? run.totalTasks : 0}</td>
          <td><strong>${run.totalWorkload != null ? Number(run.totalWorkload).toFixed(1) : '0.0'}</strong></td>
          <td><span class="badge badge-green">${escapeHtml(run.status || 'SUCCESS')}</span></td>
        </tr>
      `;
    }).join('');
  }

  function appendTrace(msg) {
    if (!traceTerminal) return;
    traceTerminal.textContent += msg + '\n';
    traceTerminal.scrollTop = traceTerminal.scrollHeight;
  }

  function setLoading(loading) {
    if (btnRun) btnRun.disabled = loading;
    if (runSpinner) runSpinner.style.display = loading ? 'inline-block' : 'none';
    if (runBtnText) runBtnText.textContent = loading ? 'Processing...' : '⚡ Run MapReduce';
  }

  function setPipelineLoading(loading) {
    if (btnRunPipeline) {
      btnRunPipeline.disabled = loading;
      btnRunPipeline.textContent = loading ? 'Dispatching...' : '🚀 Run Partitioned Pipeline';
    }
  }

  function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // ── Mock Helpers ──
  function getMockTasks() {
    return [
      { taskId: 'TASK-PART-A1', description: 'Cluster-A: Aerial thermal scan for trapped survivors in Sector 1', workload: 3.0, clusterId: 'Cluster-A', type: 'Search', status: 'ASSIGNED', assignedNodeId: 'UAV-01', assignedByLeaderId: 'UAV-02', createdAt: new Date(Date.now() - 600000).toISOString(), assignedAt: new Date(Date.now() - 590000).toISOString() },
      { taskId: 'TASK-PART-A2', description: 'Cluster-A: Emergency trauma antibiotic and bandage air-drop', workload: 4.0, clusterId: 'Cluster-A', type: 'Medical', status: 'ASSIGNED', assignedNodeId: 'UAV-02', assignedByLeaderId: 'UAV-02', createdAt: new Date(Date.now() - 500000).toISOString(), assignedAt: new Date(Date.now() - 490000).toISOString() },
      { taskId: 'TASK-PART-A3', description: 'Cluster-A: High-calorie ration pack delivery to shelter', workload: 2.0, clusterId: 'Cluster-A', type: 'Food', status: 'ASSIGNED', assignedNodeId: 'UAV-01', assignedByLeaderId: 'UAV-02', createdAt: new Date(Date.now() - 400000).toISOString(), assignedAt: new Date(Date.now() - 390000).toISOString() },
      { taskId: 'TASK-PART-B1', description: 'Cluster-B: Ground sonar search in collapsed municipal building', workload: 5.0, clusterId: 'Cluster-B', type: 'Search', status: 'ASSIGNED', assignedNodeId: 'Edge-01', assignedByLeaderId: 'Edge-01', createdAt: new Date(Date.now() - 300000).toISOString(), assignedAt: new Date(Date.now() - 290000).toISOString() },
      { taskId: 'TASK-PART-B2', description: 'Cluster-B: Field clinic mobile saline and triage replenishment', workload: 3.0, clusterId: 'Cluster-B', type: 'Medical', status: 'ASSIGNED', assignedNodeId: 'Edge-02', assignedByLeaderId: 'Edge-01', createdAt: new Date(Date.now() - 200000).toISOString(), assignedAt: new Date(Date.now() - 190000).toISOString() },
      { taskId: 'TASK-PART-B3', description: 'Cluster-B: Portable water purification unit transport', workload: 2.0, clusterId: 'Cluster-B', type: 'Food', status: 'ASSIGNED', assignedNodeId: 'Edge-01', assignedByLeaderId: 'Edge-01', createdAt: new Date(Date.now() - 100000).toISOString(), assignedAt: new Date(Date.now() - 90000).toISOString() }
    ];
  }

  function getMockPipelineResults() {
    return [
      { taskId: 'TASK-PART-A1', taskDescription: 'Cluster-A: Aerial thermal scan', workload: 3.0, clusterId: 'Cluster-A', chosenNodeId: 'UAV-01', assignedByLeaderId: 'UAV-02', success: true, steps: ['[PIPELINE] UAV-02 assigned TASK-PART-A1 to UAV-01'] },
      { taskId: 'TASK-PART-B1', taskDescription: 'Cluster-B: Ground sonar search', workload: 5.0, clusterId: 'Cluster-B', chosenNodeId: 'Edge-01', assignedByLeaderId: 'Edge-01', success: true, steps: ['[PIPELINE] Edge-01 assigned TASK-PART-B1 to Edge-01'] }
    ];
  }

})();
