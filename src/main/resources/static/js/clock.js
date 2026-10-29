/**
 * clock.js — Frontend logic for Clock Synchronization & Vector Clocks (Exp 3)
 * Author: Prasham (own page per ownership rules)
 */

(function () {
  'use strict';

  // ── DOM References ──
  const tbody             = document.getElementById('clocks-tbody');
  const btnSyncAll        = document.getElementById('btn-sync-all');
  const btnTogglePart     = document.getElementById('btn-toggle-partition');
  const btnRefresh        = document.getElementById('btn-refresh');
  const btnResetSkews     = document.getElementById('btn-reset-skews');
  const btnClearTrace     = document.getElementById('btn-clear-trace');
  const traceEl           = document.getElementById('step-trace');

  const bannerEl          = document.getElementById('partition-banner');
  const bannerIcon        = document.getElementById('banner-icon');
  const bannerTitle       = document.getElementById('banner-title');
  const bannerSub         = document.getElementById('banner-sub');
  const bannerBadge       = document.getElementById('banner-badge');

  const kpiCoordTime      = document.getElementById('kpi-coord-time');
  const kpiSyncedCount    = document.getElementById('kpi-synced-count');
  const kpiSyncedMeta     = document.getElementById('kpi-synced-meta');
  const kpiAvgRtt         = document.getElementById('kpi-avg-rtt');
  const kpiNetStatus      = document.getElementById('kpi-net-status');
  const kpiNetBadge       = document.getElementById('kpi-net-badge');

  // ── State ──
  let isPartitioned = false;
  let chartInstance = null;

  // ── Init ──
  document.addEventListener('DOMContentLoaded', () => {
    loadClockStatus();
    initLiveLog();
    subscribeToEvents();
    startClockTicker();

    btnSyncAll.addEventListener('click', syncAllClocks);
    btnTogglePart.addEventListener('click', toggleNetworkPartition);
    btnRefresh.addEventListener('click', loadClockStatus);
    btnResetSkews.addEventListener('click', resetDefaultSkews);
    btnClearTrace.addEventListener('click', () => {
      traceEl.innerHTML = '<div style="color:#64748b;">Cleared.</div>';
    });
  });

  // ── Load & Render Telemetry ──
  async function loadClockStatus() {
    try {
      const data = await api.get('/api/clock/status');
      isPartitioned = !!data.partitioned;
      updatePartitionUI(isPartitioned);
      renderTable(data.nodes || []);
      updateKPIs(data.nodes || []);
      renderChart(data.nodes || []);
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="10" style="text-align:center; padding:20px; color:#ef4444;">
        Failed to fetch clock status: ${escapeHtml(err.message)}</td></tr>`;
    }
  }

  function renderTable(nodes) {
    if (!Array.isArray(nodes) || nodes.length === 0) {
      tbody.innerHTML = `<tr><td colspan="10" style="text-align:center; padding:24px; color:#94a3b8;">
        No nodes found. Ensure nodes are registered.</td></tr>`;
      return;
    }

    tbody.innerHTML = nodes.map(n => {
      const skewFormatted = (n.injectedSkewMillis >= 0 ? '+' : '') + n.injectedSkewMillis + ' ms';
      const offsetFormatted = n.synced ? (n.offsetMillis >= 0 ? '+' : '') + n.offsetMillis + ' ms' : '—';
      const rttFormatted = n.synced ? n.rttMillis + ' ms' : '—';

      const rawTimeStr = new Date(n.rawLocalTime).toLocaleTimeString() + '.' + String(n.rawLocalTime % 1000).padStart(3, '0');
      const correctedTimeStr = n.synced
        ? (new Date(n.correctedTime).toLocaleTimeString() + '.' + String(n.correctedTime % 1000).padStart(3, '0'))
        : '<span style="color:#94a3b8;">Not synced</span>';

      // Status Badge
      let badgeHtml;
      if (isPartitioned) {
        badgeHtml = '<span class="badge badge-red">BLOCKED</span>';
      } else if (n.synced) {
        badgeHtml = '<span class="badge badge-green">SYNCED</span>';
      } else {
        badgeHtml = '<span class="badge badge-amber">DRIFTING</span>';
      }

      // Vector Clock snapshot
      let vcDisplay = '—';
      if (n.vectorClock && Object.keys(n.vectorClock).length > 0) {
        const pairs = Object.entries(n.vectorClock).map(([k, v]) => `${k}:${v}`).join(', ');
        vcDisplay = `<code style="font-size:0.78rem; color:#2563eb;">&lt;${escapeHtml(pairs)}&gt;</code>`;
      } else {
        vcDisplay = `<span style="font-size:0.78rem; color:#94a3b8;">&lt;idle&gt;</span>`;
      }

      return `
        <tr>
          <td><strong>${escapeHtml(n.nodeId)}</strong></td>
          <td><span class="badge badge-gray">${escapeHtml(n.clusterId || '—')}</span></td>
          <td>
            <div style="display:flex; align-items:center; gap:6px;">
              <span class="skew-pill">${skewFormatted}</span>
              <button class="btn btn-outline btn-sm edit-skew-btn" data-node="${escapeHtml(n.nodeId)}" data-skew="${n.injectedSkewMillis}" style="padding:1px 5px; font-size:0.7rem;" title="Adjust drift">✏️</button>
            </div>
          </td>
          <td><code>${rttFormatted}</code></td>
          <td><strong style="color:${n.offsetMillis !== 0 ? '#d97706' : '#10b981'};">${offsetFormatted}</strong></td>
          <td style="font-size:0.82rem; font-family:monospace; color:#64748b;">${rawTimeStr}</td>
          <td style="font-size:0.82rem; font-family:monospace; font-weight:600; color:#0f172a;">${correctedTimeStr}</td>
          <td>${vcDisplay}</td>
          <td>${badgeHtml}</td>
          <td>
            <button class="btn btn-primary btn-sm sync-node-btn" data-node="${escapeHtml(n.nodeId)}" ${isPartitioned ? 'disabled' : ''}>
              ⚡ Sync
            </button>
          </td>
        </tr>`;
    }).join('');

    // Attach listeners to sync buttons
    document.querySelectorAll('.sync-node-btn').forEach(btn => {
      btn.addEventListener('click', () => syncSingleNode(btn.dataset.node));
    });

    // Attach listeners to skew edit buttons
    document.querySelectorAll('.edit-skew-btn').forEach(btn => {
      btn.addEventListener('click', () => promptAdjustSkew(btn.dataset.node, btn.dataset.skew));
    });
  }

  function updateKPIs(nodes) {
    if (!Array.isArray(nodes)) return;
    const total = nodes.length;
    const synced = nodes.filter(n => n.synced).length;
    kpiSyncedCount.textContent = `${synced} / ${total}`;
    kpiSyncedMeta.textContent = synced === total ? 'All nodes synchronized' : `${total - synced} drifting`;

    const rtts = nodes.filter(n => n.synced && n.rttMillis > 0).map(n => n.rttMillis);
    if (rtts.length > 0) {
      const avg = Math.round(rtts.reduce((a, b) => a + b, 0) / rtts.length);
      kpiAvgRtt.textContent = `${avg} ms`;
    } else {
      kpiAvgRtt.textContent = `0 ms`;
    }
  }

  function updatePartitionUI(partitioned) {
    if (partitioned) {
      bannerEl.className = 'network-banner banner-partitioned';
      bannerIcon.textContent = '🔴';
      bannerTitle.textContent = 'Network Partition Active (Exp 3 / Exp 4 Mode)';
      bannerSub.textContent = 'Cluster-A and Cluster-B are isolated. Cluster Coordinator is UNREACHABLE for edge nodes.';
      bannerBadge.className = 'badge badge-red';
      bannerBadge.textContent = 'COORDINATOR ISOLATED';

      btnTogglePart.className = 'btn btn-success';
      btnTogglePart.textContent = '💚 Heal Network Partition';

      kpiNetStatus.textContent = 'PARTITIONED';
      kpiNetBadge.className = 'badge badge-red';
      kpiNetBadge.textContent = 'ISOLATED';
    } else {
      bannerEl.className = 'network-banner banner-normal';
      bannerIcon.textContent = '🟢';
      bannerTitle.textContent = 'Network Connected (Normal Mode)';
      bannerSub.textContent = 'All clusters can exchange updates and reach the Cluster Coordinator.';
      bannerBadge.className = 'badge badge-green';
      bannerBadge.textContent = 'COORDINATOR ONLINE';

      btnTogglePart.className = 'btn btn-danger';
      btnTogglePart.textContent = '⚡ Simulate Partition';

      kpiNetStatus.textContent = 'NORMAL';
      kpiNetBadge.className = 'badge badge-green';
      kpiNetBadge.textContent = 'REACHABLE';
    }
  }

  // ── Actions ──

  async function syncSingleNode(nodeId) {
    try {
      const res = await api.post(`/api/clock/sync/${nodeId}`);
      appendSteps(res.steps || [`[Cristian] Synchronized ${nodeId}`]);
      toast.success('Clock Synchronized', `${nodeId} offset: ${res.offsetMillis} ms (RTT: ${res.rttMillis} ms)`);
      loadClockStatus();
    } catch (err) {
      appendSteps([`[Cristian] FAILED for ${nodeId}: ${err.message}`]);
      toast.error('Sync Failed', err.message);
      loadClockStatus();
    }
  }

  async function syncAllClocks() {
    btnSyncAll.disabled = true;
    try {
      const results = await api.post('/api/clock/sync-all');
      let combinedSteps = [];
      results.forEach(r => {
        if (r.steps) combinedSteps.push(...r.steps);
      });
      appendSteps(combinedSteps);
      toast.success('Sync All Complete', `Processed Cristian sync for ${results.length} nodes`);
      loadClockStatus();
    } catch (err) {
      toast.error('Sync All Failed', err.message);
    } finally {
      btnSyncAll.disabled = false;
    }
  }

  async function toggleNetworkPartition() {
    btnTogglePart.disabled = true;
    try {
      let res;
      if (isPartitioned) {
        res = await api.post('/api/network/heal');
        appendSteps(res.steps || ['[NETWORK] Network healed.']);
        toast.success('Partition Healed', 'Coordinator and cluster links restored.');
      } else {
        res = await api.post('/api/network/partition');
        appendSteps(res.steps || ['[NETWORK] Network partitioned.']);
        toast.warning('Partition Active', 'Coordinator is unreachable. Clusters isolated.');
      }
      loadClockStatus();
    } catch (err) {
      toast.error('Network Toggle Failed', err.message);
    } finally {
      btnTogglePart.disabled = false;
    }
  }

  async function promptAdjustSkew(nodeId, currentSkew) {
    const input = prompt(`Enter simulated clock drift (in milliseconds) for ${nodeId}:`, currentSkew);
    if (input === null) return;
    const val = parseInt(input.trim(), 10);
    if (isNaN(val)) {
      alert('Please enter a valid integer (e.g. 1500, -800, 300).');
      return;
    }
    try {
      await api.post(`/api/clock/skew/${nodeId}?skew=${val}`);
      toast.info('Drift Updated', `Simulated drift for ${nodeId} set to ${val} ms`);
      loadClockStatus();
    } catch (err) {
      toast.error('Failed to update drift', err.message);
    }
  }

  async function resetDefaultSkews() {
    try {
      await api.post('/api/clock/skew/UAV-01?skew=1500');
      await api.post('/api/clock/skew/UAV-02?skew=-800');
      await api.post('/api/clock/skew/Edge-01?skew=300');
      toast.info('Skews Reset', 'Restored UAV-01 (+1500ms), UAV-02 (-800ms), Edge-01 (+300ms)');
      loadClockStatus();
    } catch (err) {
      toast.error('Failed to reset skews', err.message);
    }
  }

  // ── Chart.js Visualization ──
  function renderChart(nodes) {
    const ctx = document.getElementById('offsets-chart');
    if (!ctx || !window.Chart || !Array.isArray(nodes) || nodes.length === 0) return;

    const labels = nodes.map(n => n.nodeId);
    const skews = nodes.map(n => n.injectedSkewMillis);
    const offsets = nodes.map(n => n.offsetMillis);
    const rtts = nodes.map(n => n.rttMillis);

    if (chartInstance) {
      chartInstance.destroy();
    }

    chartInstance = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [
          {
            label: 'Injected Clock Drift (ms)',
            data: skews,
            backgroundColor: 'rgba(239, 68, 68, 0.75)',
            borderColor: '#ef4444',
            borderWidth: 1,
            borderRadius: 4
          },
          {
            label: 'Calculated Offset (ms)',
            data: offsets,
            backgroundColor: 'rgba(37, 99, 235, 0.75)',
            borderColor: '#2563eb',
            borderWidth: 1,
            borderRadius: 4
          },
          {
            label: 'Round-Trip Time RTT (ms)',
            data: rtts,
            backgroundColor: 'rgba(16, 185, 129, 0.75)',
            borderColor: '#10b981',
            borderWidth: 1,
            borderRadius: 4
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          y: {
            grid: { color: '#f1f5f9' },
            title: { display: true, text: 'Milliseconds (ms)' }
          },
          x: {
            grid: { display: false }
          }
        },
        plugins: {
          legend: { position: 'top' },
          tooltip: {
            callbacks: {
              label: function (ctx) {
                return `${ctx.dataset.label}: ${ctx.raw} ms`;
              }
            }
          }
        }
      }
    });
  }

  // ── Step Trace & Live Log ──
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
      events.createLiveLog('clock-live-log', {
        maxEntries: 60,
        filterModule: 'core'
      });
    }
  }

  function subscribeToEvents() {
    if (!window.events) return;
    events.on('core', 'CLOCK_SYNCED', () => loadClockStatus());
    events.on('core', 'NETWORK_PARTITIONED', () => loadClockStatus());
    events.on('core', 'NETWORK_HEALED', () => loadClockStatus());
  }

  function startClockTicker() {
    setInterval(() => {
      const now = new Date();
      if (kpiCoordTime) {
        kpiCoordTime.textContent = now.toLocaleTimeString();
      }
    }, 1000);
  }

  function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }
})();

