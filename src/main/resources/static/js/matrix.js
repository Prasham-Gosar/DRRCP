/**
 * matrix.js - Frontend controller for Parallel Matrix Multiplication (Experiment 10)
 * Author: Vedant (Frontend Shell & Parallel Computing)
 *
 * Implements:
 * - Benchmark execution & live metrics telemetry
 * - Thread sweep scaling analysis with Chart.js visualizations
 * - Interactive Top-Left Risk Priority sample matrix with heatmap styling
 * - Chronological terminal trace & SSE event integration
 * - Mock mode fallback (?mock=1)
 */

(function () {
  'use strict';

  let chartSpeedupInstance = null;
  let chartLatencyInstance = null;

  document.addEventListener('DOMContentLoaded', initMatrixDashboard);

  function initMatrixDashboard() {
    // 1. Initialize SSE Live Log widget filtered for comm module
    if (window.events && typeof window.events.createLiveLog === 'function') {
      window.events.createLiveLog('matrix-live-log', { filterModule: 'comm', maxEntries: 40 });
    }

    // 2. Setup inputs and controls
    const slider = document.getElementById('input-n-slider');
    const labelN = document.getElementById('label-n');
    const btnRun = document.getElementById('btn-run');
    const btnSweep = document.getElementById('btn-sweep');
    const btnClearTrace = document.getElementById('btn-clear-trace');
    const presetButtons = document.querySelectorAll('.btn-preset');

    if (slider && labelN) {
      slider.addEventListener('input', (e) => {
        const val = e.target.value;
        labelN.textContent = `${val} x ${val}`;
      });
    }

    presetButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        const presetN = btn.getAttribute('data-n');
        if (slider && labelN) {
          slider.value = presetN;
          labelN.textContent = `${presetN} x ${presetN}`;
        }
      });
    });

    if (btnRun) {
      btnRun.addEventListener('click', runSingleBenchmark);
    }

    if (btnSweep) {
      btnSweep.addEventListener('click', runThreadSweep);
    }

    if (btnClearTrace) {
      btnClearTrace.addEventListener('click', () => {
        const traceEl = document.getElementById('matrix-trace-log');
        if (traceEl) traceEl.innerHTML = '<div style="color: #64748b; font-style: italic;">Log cleared.</div>';
      });
    }

    // 3. Initialize empty charts
    initCharts();

    // 4. Initial placeholder sample run or mock
    const isMock = (window.api && window.api.isMock && window.api.isMock()) ||
                   new URLSearchParams(window.location.search).get('mock') === '1';
    if (isMock) {
      runSingleBenchmark();
    }
  }

  function getRunPayload() {
    const slider = document.getElementById('input-n-slider');
    const threadsSelect = document.getElementById('select-threads');
    const maxThreadsSelect = document.getElementById('select-max-threads');

    const n = parseInt(slider ? slider.value : 400, 10);
    const threads = parseInt(threadsSelect ? threadsSelect.value : 4, 10);
    const maxThreads = parseInt(maxThreadsSelect ? maxThreadsSelect.value : 8, 10);

    return { n, threads, maxThreads };
  }

  async function runSingleBenchmark() {
    const payload = getRunPayload();
    setBusy(true);

    try {
      let result;
      const isMock = (window.api && window.api.isMock && window.api.isMock()) ||
                     new URLSearchParams(window.location.search).get('mock') === '1';

      if (isMock) {
        result = generateMockRunResult(payload.n, payload.threads);
        await new Promise(r => setTimeout(r, 400));
      } else {
        result = await api.post('/api/matrix/run', { n: payload.n, threads: payload.threads });
      }

      renderBenchmarkResult(result);
      if (window.toast) {
        window.toast.success('Matrix Benchmark Complete', `${result.n}x${result.n} multiplied in ${result.parallelMs} ms (${result.speedup}x speedup).`);
      }
    } catch (err) {
      console.error('[Matrix] Benchmark failed:', err);
      if (window.toast) {
        window.toast.error('Execution Failed', err.message || 'Error executing matrix multiplication benchmark.');
      }
      const badge = document.getElementById('badge-correctness');
      if (badge) {
        badge.className = 'badge badge-red';
        badge.textContent = 'EXECUTION ERROR';
      }
    } finally {
      setBusy(false);
    }
  }

  async function runThreadSweep() {
    const payload = getRunPayload();
    setBusy(true);

    try {
      let sweepResult;
      const isMock = (window.api && window.api.isMock && window.api.isMock()) ||
                     new URLSearchParams(window.location.search).get('mock') === '1';

      if (isMock) {
        sweepResult = generateMockSweepResult(payload.n, payload.maxThreads);
        await new Promise(r => setTimeout(r, 700));
      } else {
        sweepResult = await api.post('/api/matrix/sweep', { n: payload.n, maxThreads: payload.maxThreads });
      }

      renderSweepResult(sweepResult);
      if (window.toast) {
        window.toast.success('Thread Sweep Complete', `Peak speedup of ${sweepResult.bestSpeedup}x achieved using ${sweepResult.bestThreads} threads.`);
      }
    } catch (err) {
      console.error('[Matrix] Sweep failed:', err);
      if (window.toast) {
        window.toast.error('Sweep Failed', err.message || 'Error running multi-threaded sweep.');
      }
    } finally {
      setBusy(false);
    }
  }

  function setBusy(isBusy) {
    const btnRun = document.getElementById('btn-run');
    const btnSweep = document.getElementById('btn-sweep');
    if (btnRun) btnRun.disabled = isBusy;
    if (btnSweep) btnSweep.disabled = isBusy;
  }

  function renderBenchmarkResult(result) {
    // 1. Update Telemetry Cards
    const valSeq = document.getElementById('val-seq-time');
    const valPar = document.getElementById('val-par-time');
    const valSpeedup = document.getElementById('val-speedup');
    const valEff = document.getElementById('val-efficiency');
    const badge = document.getElementById('badge-correctness');

    if (valSeq) valSeq.innerHTML = `${result.sequentialMs.toFixed(2)} <span style="font-size: 0.9rem; font-weight: 600;">ms</span>`;
    if (valPar) valPar.innerHTML = `${result.parallelMs.toFixed(2)} <span style="font-size: 0.9rem; font-weight: 600;">ms</span>`;
    if (valSpeedup) valSpeedup.textContent = `${result.speedup.toFixed(2)}x`;
    if (valEff) valEff.textContent = `${(result.efficiency * 100).toFixed(1)}%`;

    if (badge) {
      if (result.correct) {
        badge.className = 'badge badge-green pulse-green';
        badge.innerHTML = '🛡️ 100% MATCH (PASSED)';
      } else {
        badge.className = 'badge badge-red';
        badge.textContent = 'MISMATCH DETECTED';
      }
    }

    // 2. Render Top-Left Sample Matrix
    renderSampleGrid(result.sampleResult);

    // 3. Update Latency Bar Chart for single run
    updateLatencyChart([
      { label: `1 Thread (Sequential)`, time: result.sequentialMs, color: '#94a3b8' },
      { label: `${result.threads} Threads (Parallel)`, time: result.parallelMs, color: '#2563eb' }
    ]);

    // 4. Update Terminal Trace Log
    renderTrace(result.steps);
  }

  function renderSweepResult(sweep) {
    if (!sweep || !Array.isArray(sweep.runs)) return;

    // 1. Render Speedup Scaling Line Chart
    const threadLabels = sweep.runs.map(r => `${r.threads}T`);
    const actualSpeedups = sweep.runs.map(r => r.speedup);
    const idealSpeedups = sweep.runs.map(r => r.threads);

    updateSpeedupChart(threadLabels, actualSpeedups, idealSpeedups);

    // 2. Render Latency Comparison Bar Chart across all sweep runs
    const latencyItems = sweep.runs.map(r => ({
      label: `${r.threads} Threads`,
      time: r.parallelMs,
      color: r.threads === sweep.bestThreads ? '#10b981' : '#3b82f6'
    }));
    updateLatencyChart(latencyItems);

    // 3. Update Telemetry using best run
    const bestRun = sweep.runs.find(r => r.threads === sweep.bestThreads) || sweep.runs[sweep.runs.length - 1];
    if (bestRun) {
      const valSeq = document.getElementById('val-seq-time');
      const valPar = document.getElementById('val-par-time');
      const valSpeedup = document.getElementById('val-speedup');
      const valEff = document.getElementById('val-efficiency');
      const badge = document.getElementById('badge-correctness');

      if (valSeq) valSeq.innerHTML = `${bestRun.sequentialMs.toFixed(2)} <span style="font-size: 0.9rem; font-weight: 600;">ms</span>`;
      if (valPar) valPar.innerHTML = `${bestRun.parallelMs.toFixed(2)} <span style="font-size: 0.9rem; font-weight: 600;">ms</span>`;
      if (valSpeedup) valSpeedup.textContent = `${sweep.bestSpeedup.toFixed(2)}x`;
      if (valEff) valEff.textContent = `${(bestRun.efficiency * 100).toFixed(1)}%`;

      if (badge) {
        badge.className = 'badge badge-green pulse-green';
        badge.innerHTML = `🛡️ SWEEP BEST: ${sweep.bestThreads}T (${sweep.bestSpeedup}x)`;
      }
    }

    // 4. Trace Log
    const traceSteps = [
      `=== MULTI-THREADED BENCHMARK SWEEP (N = ${sweep.n}) ===`,
      `Testing parallel decomposition across powers of 2 up to ${sweep.maxThreads} worker threads...`,
      ...sweep.runs.map(r => `[Threads = ${r.threads}] Wall Clock: ${r.parallelMs.toFixed(2)} ms | Speedup: ${r.speedup.toFixed(2)}x | Efficiency: ${(r.efficiency * 100).toFixed(1)}% | Verified: ${r.correct ? "PASSED" : "FAILED"}`),
      `Optimal throughput achieved with ${sweep.bestThreads} threads -> Peak Speedup: ${sweep.bestSpeedup.toFixed(2)}x`
    ];
    renderTrace(traceSteps);
  }

  function renderSampleGrid(sample) {
    const container = document.getElementById('matrix-sample-container');
    if (!container || !Array.isArray(sample)) return;

    let html = '';
    const flatVals = sample.flat();
    const minVal = Math.min(...flatVals);
    const maxVal = Math.max(...flatVals);
    const range = (maxVal - minVal) || 1;

    for (let i = 0; i < sample.length; i++) {
      for (let j = 0; j < sample[i].length; j++) {
        const val = sample[i][j];
        const ratio = (val - minVal) / range;
        let cellClass = 'cell-low';
        if (ratio > 0.66) cellClass = 'cell-high';
        else if (ratio > 0.33) cellClass = 'cell-mid';

        html += `<div class="matrix-cell ${cellClass}" title="Sector Row ${i}, Resource Col ${j}">${val}</div>`;
      }
    }
    container.innerHTML = html;
  }

  function renderTrace(steps) {
    const traceContainer = document.getElementById('matrix-trace-log');
    if (!traceContainer) return;

    if (!Array.isArray(steps) || steps.length === 0) {
      traceContainer.innerHTML = '<div style="color: #64748b; font-style: italic;">Awaiting benchmark execution...</div>';
      return;
    }

    traceContainer.innerHTML = steps.map((st, i) => `
      <div class="step-row">
        <span class="step-idx">${i + 1}</span>
        <span class="step-text">${escapeHtml(st)}</span>
      </div>
    `).join('');
    traceContainer.scrollTop = traceContainer.scrollHeight;
  }

  // ── Chart.js Setup ──

  function initCharts() {
    if (typeof Chart === 'undefined') {
      console.warn('[Matrix] Chart.js not loaded, skipping chart initialization');
      return;
    }

    // 1. Line Chart: Speedup vs Threads
    const ctxSpeedup = document.getElementById('chart-speedup');
    if (ctxSpeedup) {
      chartSpeedupInstance = new Chart(ctxSpeedup, {
        type: 'line',
        data: {
          labels: ['1T', '2T', '4T', '8T'],
          datasets: [
            {
              label: 'Empirical Speedup (Exp 10)',
              data: [1.0, 1.9, 3.4, 5.8],
              borderColor: '#2563eb',
              backgroundColor: 'rgba(37, 99, 235, 0.1)',
              borderWidth: 3,
              fill: true,
              tension: 0.3,
              pointRadius: 5,
              pointBackgroundColor: '#2563eb'
            },
            {
              label: 'Ideal Linear Speedup (Theoretical)',
              data: [1.0, 2.0, 4.0, 8.0],
              borderColor: '#94a3b8',
              borderWidth: 2,
              borderDash: [5, 5],
              fill: false,
              pointRadius: 3
            }
          ]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { position: 'top', labels: { boxWidth: 12, font: { size: 11 } } },
            tooltip: { callbacks: { label: (ctx) => `${ctx.dataset.label}: ${ctx.raw}x` } }
          },
          scales: {
            y: {
              beginAtZero: true,
              title: { display: true, text: 'Speedup Factor', font: { size: 11 } }
            },
            x: {
              title: { display: true, text: 'Thread Count', font: { size: 11 } }
            }
          }
        }
      });
    }

    // 2. Bar Chart: Latency Comparison
    const ctxLatency = document.getElementById('chart-latency');
    if (ctxLatency) {
      chartLatencyInstance = new Chart(ctxLatency, {
        type: 'bar',
        data: {
          labels: ['Sequential (1T)', 'Parallel (4T)'],
          datasets: [{
            label: 'Wall Clock Duration (ms)',
            data: [145.2, 42.1],
            backgroundColor: ['#94a3b8', '#2563eb'],
            borderRadius: 6
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false },
            tooltip: { callbacks: { label: (ctx) => `${ctx.raw} ms` } }
          },
          scales: {
            y: {
              beginAtZero: true,
              title: { display: true, text: 'Execution Time (ms)', font: { size: 11 } }
            }
          }
        }
      });
    }
  }

  function updateSpeedupChart(labels, actualSpeedups, idealSpeedups) {
    if (!chartSpeedupInstance) return;
    chartSpeedupInstance.data.labels = labels;
    chartSpeedupInstance.data.datasets[0].data = actualSpeedups;
    chartSpeedupInstance.data.datasets[1].data = idealSpeedups;
    chartSpeedupInstance.update();
  }

  function updateLatencyChart(items) {
    if (!chartLatencyInstance) return;
    chartLatencyInstance.data.labels = items.map(it => it.label);
    chartLatencyInstance.data.datasets[0].data = items.map(it => it.time);
    chartLatencyInstance.data.datasets[0].backgroundColor = items.map(it => it.color);
    chartLatencyInstance.update();
  }

  // ── Mock Data Generator ──

  function generateMockRunResult(n, threads) {
    const seqMs = Math.round((n * n * n) / 450000.0 * 100) / 100.0;
    const eff = 0.88 - (threads * 0.02);
    const parMs = Math.max(1.0, Math.round((seqMs / (threads * eff)) * 100) / 100.0);
    const speedup = Math.round((seqMs / parMs) * 100) / 100.0;
    const efficiency = Math.round((speedup / threads) * 100) / 100.0;

    const sample = [];
    for (let i = 0; i < 4; i++) {
      const row = [];
      for (let j = 0; j < 4; j++) {
        row.push(1400 + i * 170 + j * 170);
      }
      sample.push(row);
    }

    return {
      n,
      threads,
      sequentialMs: seqMs,
      parallelMs: parMs,
      speedup: speedup,
      efficiency: efficiency,
      correct: true,
      sampleResult: sample,
      steps: [
        `Initialized ${n} x ${n} Zone-Risk (A) and Resource-Demand (B) matrices...`,
        `Sequential execution baseline: ${seqMs.toFixed(2)} ms`,
        `Parallel row-scatter across ${threads} threads: ${parMs.toFixed(2)} ms`,
        `Correctness Verification: PASSED (100% Match)`,
        `Speedup: ${speedup.toFixed(2)}x | Parallel Efficiency: ${(efficiency * 100).toFixed(1)}%`
      ],
      completedAt: new Date().toISOString()
    };
  }

  function generateMockSweepResult(n, maxThreads) {
    const seqMs = Math.round((n * n * n) / 450000.0 * 100) / 100.0;
    const runs = [];
    const threadSteps = [1, 2, 4, 8, 16, 32].filter(t => t <= maxThreads);
    if (!threadSteps.includes(maxThreads)) threadSteps.push(maxThreads);

    let bestThreads = 1;
    let bestSpeedup = 1.0;

    for (const t of threadSteps) {
      const eff = Math.max(0.60, 0.94 - (t * 0.025));
      const parMs = t === 1 ? seqMs : Math.max(1.0, Math.round((seqMs / (t * eff)) * 100) / 100.0);
      const speedup = Math.round((seqMs / parMs) * 100) / 100.0;
      const efficiency = Math.round((speedup / t) * 100) / 100.0;

      if (speedup > bestSpeedup) {
        bestSpeedup = speedup;
        bestThreads = t;
      }

      runs.push({
        n,
        threads: t,
        sequentialMs: seqMs,
        parallelMs: parMs,
        speedup,
        efficiency,
        correct: true,
        completedAt: new Date().toISOString()
      });
    }

    return {
      n,
      maxThreads,
      runs,
      bestThreads,
      bestSpeedup,
      completedAt: new Date().toISOString()
    };
  }

  function escapeHtml(str) {
    if (typeof str !== 'string') return String(str);
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

})();
