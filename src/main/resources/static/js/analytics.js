/**
 * analytics.js - Distributed MapReduce Task Analytics Dashboard (Exp 7)
 * Author: Parth (Compute & Analytics Backend)
 */

(function () {
  'use strict';

  // DOM Elements
  const btnRun = document.getElementById('btn-run-analytics');
  const runBtnText = document.getElementById('run-btn-text');
  const runSpinner = document.getElementById('run-spinner');
  const btnSeed = document.getElementById('btn-seed-tasks');
  const btnRefreshHistory = document.getElementById('btn-refresh-history');
  const btnClearTrace = document.getElementById('btn-clear-trace');
  const activeEngineBadge = document.getElementById('active-engine-badge');
  const traceTerminal = document.getElementById('trace-terminal');

  // KPI Elements
  const kpiTotalTasks = document.getElementById('kpi-total-tasks');
  const kpiTotalWorkload = document.getElementById('kpi-total-workload');
  const kpiActiveClusters = document.getElementById('kpi-active-clusters');
  const kpiDuration = document.getElementById('kpi-duration');
  const kpiEngineMode = document.getElementById('kpi-engine-mode');
  const sourceFileLabel = document.getElementById('source-file-label');

  // Table Bodies
  const clusterTableBody = document.getElementById('cluster-table-body');
  const typeTableBody = document.getElementById('type-table-body');
  const breakdownTableBody = document.getElementById('breakdown-table-body');
  const historyTableBody = document.getElementById('history-table-body');

  // Chart instance
  let analyticsChart = null;

  // Initialize
  document.addEventListener('DOMContentLoaded', () => {
    initButtons();
    initChart();
    initEvents();
    loadLatest();
    loadHistory();
  });

  function initButtons() {
    if (btnRun) {
      btnRun.addEventListener('click', () => {
        const selectedRadio = document.querySelector('input[name="engineSelect"]:checked');
        const engine = selectedRadio ? selectedRadio.value : 'streams';
        runMapReduce(engine);
      });
    }

    if (btnSeed) {
      btnSeed.addEventListener('click', seedTasks);
    }

    if (btnRefreshHistory) {
      btnRefreshHistory.addEventListener('click', loadHistory);
    }

    if (btnClearTrace) {
      btnClearTrace.addEventListener('click', () => {
        if (traceTerminal) traceTerminal.textContent = '[TRACE CLEARED] Ready for next MapReduce execution.\n';
      });
    }
  }

  function initEvents() {
    if (window.events && typeof window.events.createLiveLog === 'function') {
      window.events.createLiveLog('live-event-log', { filterModule: 'compute', maxEntries: 40 });
    }

    if (window.events && typeof window.events.on === 'function') {
      window.events.on('compute', 'ANALYTICS_STARTED', (payload) => {
        appendTrace(`[EVENT: ANALYTICS_STARTED] Engine: ${payload.engine || 'unknown'} | Task count: ${payload.taskCount || '--'}`);
      });

      window.events.on('compute', 'ANALYTICS_DONE', (payload) => {
        appendTrace(`[EVENT: ANALYTICS_DONE] Completed in ${payload.durationMs || 0} ms | Engine: ${payload.engine || 'unknown'}`);
        loadHistory();
      });
    }
  }

  function initChart() {
    const canvas = document.getElementById('analyticsChart');
    if (!canvas || typeof Chart === 'undefined') return;

    const ctx = canvas.getContext('2d');
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
        interaction: {
          mode: 'index',
          intersect: false
        },
        scales: {
          y: {
            type: 'linear',
            display: true,
            position: 'left',
            title: {
              display: true,
              text: 'Workload (Weight Units)',
              font: { size: 11, weight: '600' }
            },
            beginAtZero: true
          },
          y1: {
            type: 'linear',
            display: true,
            position: 'right',
            title: {
              display: true,
              text: 'Tasks (#)',
              font: { size: 11, weight: '600' }
            },
            grid: {
              drawOnChartArea: false
            },
            beginAtZero: true,
            ticks: {
              precision: 0
            }
          }
        },
        plugins: {
          legend: {
            position: 'top',
            labels: { boxWidth: 12, font: { size: 12 } }
          },
          tooltip: {
            padding: 10,
            cornerRadius: 6
          }
        }
      }
    });
  }

  async function runMapReduce(engine) {
    setLoading(true);
    appendTrace(`\n======================================================================`);
    appendTrace(`>>> DISPATCHING MAPREDUCE ANALYTICS (Requested Engine: ${engine.toUpperCase()})`);
    appendTrace(`======================================================================`);

    try {
      let result;
      if (window.api && window.api.isMock()) {
        result = await window.api.get('/mock/analytics_latest.json');
        result.engine = engine;
      } else {
        result = await window.api.post(`/api/analytics/mapreduce/run?engine=${encodeURIComponent(engine)}`, { engine });
      }

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
      appendTrace(`[ERROR] Execution failed: ${err.message || err}`);
      if (window.toast) {
        window.toast.error('Analytics Error', err.message || 'Failed to complete MapReduce run');
      }
    } finally {
      setLoading(false);
    }
  }

  async function loadLatest() {
    try {
      let result;
      if (window.api && window.api.isMock()) {
        result = await window.api.get('/mock/analytics_latest.json');
      } else {
        result = await window.api.get('/api/analytics/mapreduce/latest');
      }
      if (result) {
        renderAnalyticsResult(result);
      }
    } catch (err) {
      console.warn('No prior analytics run or failed to fetch latest:', err);
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

  async function seedTasks() {
    try {
      if (window.api && window.api.isMock()) {
        if (window.toast) window.toast.success('Tasks Seeded', 'Seeded 10 disaster relief rescue tasks (Mock).');
        await runMapReduce('streams');
        return;
      }

      const res = await window.api.post('/api/analytics/mapreduce/seed');
      if (window.toast) {
        window.toast.success('Tasks Seeded', res.message || 'Seeded 10 disaster relief rescue tasks.');
      }
      // Re-run analytics to show updated dataset
      const selectedRadio = document.querySelector('input[name="engineSelect"]:checked');
      const engine = selectedRadio ? selectedRadio.value : 'streams';
      await runMapReduce(engine);
    } catch (err) {
      console.error('Failed to seed tasks:', err);
      if (window.toast) window.toast.error('Seed Error', err.message || 'Could not seed tasks');
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

    // 2. Update KPIs
    if (kpiTotalTasks) kpiTotalTasks.textContent = res.totalTasks != null ? res.totalTasks : 0;
    if (kpiTotalWorkload) kpiTotalWorkload.textContent = res.totalWorkload != null ? Number(res.totalWorkload).toFixed(1) : '0.0';
    
    const clusterCount = res.countsPerCluster ? Object.keys(res.countsPerCluster).length : 0;
    if (kpiActiveClusters) kpiActiveClusters.textContent = clusterCount;

    if (kpiDuration) kpiDuration.textContent = `${res.durationMs != null ? res.durationMs : 0} ms`;
    if (kpiEngineMode) kpiEngineMode.textContent = (res.engine || 'streams').toUpperCase();

    if (sourceFileLabel && res.inputFile) {
      sourceFileLabel.textContent = res.inputFile;
    }

    // 3. Render Per-Cluster Table
    renderClusterTable(res.countsPerCluster || {}, res.workloadPerCluster || {}, res.totalWorkload || 0);

    // 4. Render Per-Type Table
    renderTypeTable(res.countsPerType || {}, res.workloadPerType || {});

    // 5. Render MapReduce Key Breakdown Table
    renderBreakdownTable(res.breakdown || []);

    // 6. Update Chart.js
    updateChart(res.countsPerCluster || {}, res.workloadPerCluster || {});

    // 7. Output Steps to Monospace Terminal
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

  function updateChart(counts, workloads) {
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

  function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

})();

