(function () {
  'use strict';

  const tbody = document.getElementById('nodes-tbody');
  const btnStart = document.getElementById('btn-start');
  const btnStop = document.getElementById('btn-stop');
  const btnRefresh = document.getElementById('btn-refresh');
  const btnClear = document.getElementById('btn-clear-trace');
  const traceEl = document.getElementById('step-trace');

  const kpiTotal = document.getElementById('kpi-total');
  const kpiAlive = document.getElementById('kpi-alive');
  const kpiSilent = document.getElementById('kpi-silent');
  const kpiTotalM = document.getElementById('kpi-total-meta');
  const kpiSilentB = document.getElementById('kpi-silent-badge');

  let simulatorRunning = false;

  document.addEventListener('DOMContentLoaded', () => {
    loadNodes();
    initLiveLog();
    subscribeToEvents();

    btnStart.addEventListener('click', startSimulator);
    btnStop.addEventListener('click', stopSimulator);
    btnRefresh.addEventListener('click', loadNodes);
    btnClear.addEventListener('click', () => {
      traceEl.innerHTML = '<div style="color:#64748b;">Cleared.</div>';
    });
  });

  async function loadNodes() {
    try {
      const nodes = await api.get('/api/nodes');
      renderTable(nodes);
      updateKpis(nodes);
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="11" style="text-align:center; padding:20px; color:#ef4444;">
        Failed to load nodes: ${escapeHtml(err.message)}</td></tr>`;
    }
  }

  function renderTable(nodes) {
    if (!Array.isArray(nodes) || nodes.length === 0) {
      tbody.innerHTML = `<tr><td colspan="11" style="text-align:center; padding:24px; color:#94a3b8;">
        No nodes registered. Click <strong>Start Simulator</strong> to begin the demo.</td></tr>`;
      return;
    }

    tbody.innerHTML = nodes.map(n => {
      const isAlive = (n.status === 'ALIVE');
      const statusBadge = isAlive
        ? '<span class="badge badge-green">ALIVE</span>'
        : '<span class="badge badge-red">SILENT</span>';

      const stateLabel = n.connectivityScore >= 150 ? 'STABLE'
        : n.connectivityScore >= 100 ? 'AT_RISK'
          : n.connectivityScore >= 50 ? 'PARTITION_LIKELY'
            : 'PARTITIONED';
      const stateBadge = stateLabel === 'STABLE'
        ? `<span class="badge badge-green">${stateLabel}</span>`
        : stateLabel === 'AT_RISK'
          ? `<span class="badge badge-amber">${stateLabel}</span>`
          : `<span class="badge badge-red">${stateLabel}</span>`;

      const batteryClass = n.battery > 50 ? 'progress-success' : (n.battery > 20 ? 'progress-warning' : 'progress-danger');
      const signalClass = n.signal > 50 ? 'progress-success' : (n.signal > 20 ? 'progress-warning' : 'progress-danger');

      const hbTime = n.lastHeartbeat
        ? new Date(n.lastHeartbeat).toLocaleTimeString()
        : '—';

      return `
        <tr>
          <td><strong>${escapeHtml(n.nodeId)}</strong></td>
          <td>${escapeHtml(n.nodeType || '—')}</td>
          <td>${escapeHtml(n.clusterId || '—')}</td>
          <td>
            <div style="display:flex; align-items:center; gap:6px;">
              <span>${n.battery}%</span>
              <div class="progress" style="width:50px;">
                <div class="progress-bar ${batteryClass}" style="width:${n.battery}%;"></div>
              </div>
            </div>
          </td>
          <td>
            <div style="display:flex; align-items:center; gap:6px;">
              <span>${n.signal}%</span>
              <div class="progress" style="width:50px;">
                <div class="progress-bar ${signalClass}" style="width:${n.signal}%;"></div>
              </div>
            </div>
          </td>
          <td><span class="badge badge-info">${n.connectivityScore}</span></td>
          <td>${stateBadge}</td>
          <td>${statusBadge}</td>
          <td style="font-size:0.82rem; color:#64748b;">${hbTime}</td>
          <td><code style="font-size:0.78rem;">${escapeHtml(n.lastThreadName || '—')}</code></td>
          <td>
            <button class="btn btn-outline btn-sm silence-btn" data-node="${escapeHtml(n.nodeId)}"
                    ${!simulatorRunning || !isAlive ? 'disabled' : ''}>
              🔇 Silence
            </button>
          </td>
        </tr>`;
    }).join('');

    document.querySelectorAll('.silence-btn').forEach(btn => {
      btn.addEventListener('click', () => silenceNode(btn.dataset.node));
    });
  }

  function updateKpis(nodes) {
    if (!Array.isArray(nodes)) return;
    const total = nodes.length;
    const alive = nodes.filter(n => n.status === 'ALIVE').length;
    const silent = total - alive;

    kpiTotal.textContent = total;
    kpiAlive.textContent = alive;
    kpiSilent.textContent = silent;
    kpiTotalM.textContent = total === 0 ? 'Waiting for data…' : `${total} registered node${total !== 1 ? 's' : ''}`;
    kpiSilentB.textContent = silent === 0 ? 'OK' : `${silent} ALERT`;
    kpiSilentB.className = silent === 0 ? 'badge badge-green' : 'badge badge-red';
  }

  async function startSimulator() {
    btnStart.disabled = true;
    try {
      const res = await api.post('/api/simulator/start');
      simulatorRunning = true;
      btnStop.disabled = false;
      appendSteps(res.steps || ['Simulator started']);
      toast.success('Simulator Started', `Spawned ${(res.activeNodes || []).length} nodes`);
      setTimeout(loadNodes, 800);
    } catch (err) {
      toast.error('Start Failed', err.message);
      btnStart.disabled = false;
    }
  }

  async function stopSimulator() {
    btnStop.disabled = true;
    try {
      const res = await api.post('/api/simulator/stop');
      simulatorRunning = false;
      btnStart.disabled = false;
      appendSteps(res.steps || ['Simulator stopped']);
      toast.info('Simulator Stopped', 'All heartbeat threads interrupted');
      setTimeout(loadNodes, 500);
    } catch (err) {
      toast.error('Stop Failed', err.message);
      btnStop.disabled = false;
    }
  }

  async function silenceNode(nodeId) {
    try {
      const res = await api.post(`/api/simulator/silence/${nodeId}`);
      appendSteps(res.steps || [`Silenced ${nodeId}`]);
      toast.warning('Node Silenced', `${nodeId} — silent detection should trigger in ~6 s`);
    } catch (err) {
      toast.error('Silence Failed', err.message);
    }
  }

  function appendSteps(steps) {
    if (!steps || steps.length === 0) return;
    if (traceEl.querySelector('div[style]')) {
      traceEl.innerHTML = '';
    }

    const now = new Date().toLocaleTimeString();
    steps.forEach((step, i) => {
      const row = document.createElement('div');
      row.className = 'step-row';
      row.innerHTML = `
        <span class="step-idx">${i + 1}.</span>
        <span class="log-time">${now}</span>
        <span class="step-text">${escapeHtml(step)}</span>`;
      traceEl.appendChild(row);
    });
    traceEl.scrollTop = traceEl.scrollHeight;
  }

  function initLiveLog() {
    if (window.events && typeof events.createLiveLog === 'function') {
      events.createLiveLog('nodes-live-log', {
        maxEntries: 60,
        filterModule: 'core'
      });
    }
  }

  function subscribeToEvents() {
    if (!window.events) return;
    events.on('core', 'NODE_REGISTERED', () => loadNodes());
    events.on('core', 'HEARTBEAT', () => loadNodes());
    events.on('core', 'NODE_SILENT', (payload) => {
      loadNodes();
      toast.error('Node Silent!',
        `${payload.nodeId} has been silent for ${Math.round(payload.silentForMs / 1000)}s (threshold: ${payload.thresholdMs / 1000}s)`);
    });
  }

  function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }
})();