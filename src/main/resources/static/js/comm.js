/**
 * comm.js - Frontend controller for MPI Collective Communication (Experiment 9)
 * Author: Vedant (Frontend Shell & Distributed Communication)
 *
 * Provides:
 * - Dynamic rank box row rendering (Command Centre + Rescue Team Ranks 1..N-1)
 * - Animated SVG arrow connection flows for Bcast, Scatter, and Gather
 * - Interactive controls for processes (2..16) and chunkSize (1..10)
 * - Self-check verification badge & gathered buffer inspector
 * - Per-rank timeline table and terminal step trace
 * - Live SSE events integration for module: comm
 * - Mock mode fallback (?mock=1)
 */

(function () {
  'use strict';

  // Module state
  let currentProcesses = 4;
  let currentChunkSize = 2;
  let currentRootRank = 0;
  let currentAnimationTimer = null;

  document.addEventListener('DOMContentLoaded', initCommPage);

  function initCommPage() {
    // 1. Initialize SSE live log for collective comm
    if (window.events && typeof window.events.createLiveLog === 'function') {
      window.events.createLiveLog('comm-live-log', { filterModule: 'comm', maxEntries: 40 });
    }

    // 2. Setup control event listeners
    const ranksSelect = document.getElementById('input-ranks');
    const chunkSelect = document.getElementById('input-chunk');
    const rootInput = document.getElementById('input-root');
    const alertInput = document.getElementById('alert-text');

    if (ranksSelect) {
      ranksSelect.addEventListener('change', (e) => {
        currentProcesses = parseInt(e.target.value, 10);
        updateRootRankBounds();
        renderRankRow(currentProcesses, currentRootRank);
        drawConnectionArrows();
        updateSizeBadges();
      });
    }

    if (chunkSelect) {
      chunkSelect.addEventListener('change', (e) => {
        currentChunkSize = parseInt(e.target.value, 10);
        updateSizeBadges();
      });
    }

    if (rootInput) {
      rootInput.addEventListener('change', (e) => {
        currentRootRank = Math.max(0, Math.min(currentProcesses - 1, parseInt(e.target.value, 10) || 0));
        rootInput.value = currentRootRank;
        renderRankRow(currentProcesses, currentRootRank);
        drawConnectionArrows();
      });
    }

    // 3. Action buttons
    const btnRunAll = document.getElementById('btn-run-all');
    const btnBcast = document.getElementById('btn-step-bcast');
    const btnScatter = document.getElementById('btn-step-scatter');
    const btnGather = document.getElementById('btn-step-gather');
    const btnClearTrace = document.getElementById('btn-clear-trace');
    const btnPresetAlert = document.getElementById('btn-preset-alert');

    if (btnRunAll) btnRunAll.addEventListener('click', () => executeComm('/api/comm/run-all', 'RUN_ALL'));
    if (btnBcast) btnBcast.addEventListener('click', () => executeComm('/api/comm/bcast', 'BCAST'));
    if (btnScatter) btnScatter.addEventListener('click', () => executeComm('/api/comm/scatter', 'SCATTER'));
    if (btnGather) btnGather.addEventListener('click', () => executeComm('/api/comm/gather', 'GATHER'));
    
    if (btnClearTrace) {
      btnClearTrace.addEventListener('click', () => {
        const traceEl = document.getElementById('comm-trace-log');
        if (traceEl) traceEl.innerHTML = '<div style="color: #64748b; font-style: italic;">Trace log cleared.</div>';
      });
    }

    if (btnPresetAlert) {
      btnPresetAlert.addEventListener('click', cycleAlertPreset);
    }

    // 4. Window resize handler for SVG canvas
    window.addEventListener('resize', debounce(drawConnectionArrows, 150));

    // 5. Initial render
    renderRankRow(currentProcesses, currentRootRank);
    drawConnectionArrows();
    updateSizeBadges();
  }

  function updateRootRankBounds() {
    const rootInput = document.getElementById('input-root');
    if (rootInput) {
      rootInput.max = currentProcesses - 1;
      if (currentRootRank >= currentProcesses) {
        currentRootRank = 0;
        rootInput.value = 0;
      }
    }
  }

  function updateSizeBadges() {
    const badge = document.getElementById('comm-size-badge');
    if (badge) {
      badge.textContent = `SIZE: ${currentProcesses} RANKS | CHUNK: ${currentChunkSize}`;
    }
  }

  const ALERT_PRESETS = [
    "CRITICAL: Sector 4 Flash Flood Inundation - Immediate Evacuation & Relief Operation",
    "SEISMIC ALERT: Magnitude 6.2 Tremor in Mountain Pass - Air Recon & Medical Supply Drop",
    "WILDFIRE EMERGENCY: Rapid Front Approaching Ridge - Clear Zone Corridors Immediately",
    "COMMAND DIRECTIVE: Priority Supply Distribution & Ground Team Health Assessment"
  ];
  let presetIdx = 0;

  function cycleAlertPreset() {
    presetIdx = (presetIdx + 1) % ALERT_PRESETS.length;
    const alertInput = document.getElementById('alert-text');
    if (alertInput) {
      alertInput.value = ALERT_PRESETS[presetIdx];
    }
  }

  function getPayload() {
    const processes = parseInt(document.getElementById('input-ranks')?.value || currentProcesses, 10);
    const chunkSize = parseInt(document.getElementById('input-chunk')?.value || currentChunkSize, 10);
    const rootRank = parseInt(document.getElementById('input-root')?.value || currentRootRank, 10);
    const alertText = document.getElementById('alert-text')?.value?.trim() || ALERT_PRESETS[0];

    return {
      processes,
      numProcesses: processes,
      chunkSize,
      rootRank,
      alertText
    };
  }

  async function executeComm(endpoint, opType) {
    const payload = getPayload();
    setButtonsBusy(true);

    // Reset status badges & trigger starting visual state
    highlightOperationStart(opType);

    try {
      let result;
      const isMock = (window.api && window.api.isMock && window.api.isMock()) ||
                     new URLSearchParams(window.location.search).get('mock') === '1';

      if (isMock) {
        result = generateMockResult(opType, payload);
        await new Promise(r => setTimeout(r, 600)); // simulated latency
      } else {
        result = await api.post(endpoint, payload);
      }

      renderResult(result);
      animateOperationFlow(result.operation || opType, result);

      if (window.toast) {
        const title = `${result.operation || opType} Completed`;
        const msg = `${result.operation} executed across ${result.processes || result.numProcesses} ranks in ${result.durationMs || 5}ms.`;
        window.toast.success(title, msg);
      }
    } catch (err) {
      console.error('[Comm] Execution failed:', err);
      if (window.toast) {
        window.toast.error('Execution Failed', err.message || 'Error executing collective communication');
      }
      const selfCheckBadge = document.getElementById('badge-verification');
      if (selfCheckBadge) {
        selfCheckBadge.className = 'badge badge-red';
        selfCheckBadge.textContent = 'EXECUTION ERROR';
      }
    } finally {
      setButtonsBusy(false);
    }
  }

  function setButtonsBusy(isBusy) {
    const buttons = ['btn-run-all', 'btn-step-bcast', 'btn-step-scatter', 'btn-step-gather'];
    buttons.forEach(id => {
      const btn = document.getElementById(id);
      if (btn) btn.disabled = isBusy;
    });
  }

  function highlightOperationStart(opType) {
    const banner = document.getElementById('flow-status-banner');
    if (banner) {
      banner.style.display = 'flex';
      banner.className = 'flow-banner active';
      banner.innerHTML = `<span class="spinner-dot"></span> Executing ${opType} across ${currentProcesses} distributed ranks...`;
    }
  }

  /**
   * Renders the rank cards arranged horizontally in a clean, responsive row.
   */
  function renderRankRow(count, root) {
    const rowContainer = document.getElementById('rank-boxes-row');
    if (!rowContainer) return;

    let html = '';
    for (let r = 0; r < count; r++) {
      const isRoot = (r === root);
      html += `
        <div class="rank-box ${isRoot ? 'rank-box-root' : 'rank-box-worker'}" id="rank-box-${r}" data-rank="${r}">
          <div class="rank-box-header">
            <div class="rank-title-group">
              <span class="rank-icon">${isRoot ? '🏛️' : '🚁'}</span>
              <div class="rank-name-label">
                <strong>Rank ${r}</strong>
                <span class="rank-subtext">${isRoot ? 'Command Centre' : 'Rescue Team ' + r}</span>
              </div>
            </div>
            <span class="rank-role-pill ${isRoot ? 'pill-root' : 'pill-worker'}">${isRoot ? 'ROOT' : 'WORKER'}</span>
          </div>

          <div class="rank-box-body">
            <!-- Alert Section -->
            <div class="rank-field-group">
              <div class="field-label">📢 Alert Received</div>
              <div class="field-value-box text-ellipsis" id="rank-${r}-alert" title="No alert broadcast received">
                <span class="placeholder-muted">Awaiting Bcast</span>
              </div>
            </div>

            <!-- Scatter Zones Section -->
            <div class="rank-field-group">
              <div class="field-label">🗺️ Assigned Zones (Scatter)</div>
              <div class="field-tags-container" id="rank-${r}-zones">
                <span class="placeholder-muted">None assigned</span>
              </div>
            </div>

            <!-- Gather Report Section -->
            <div class="rank-field-group">
              <div class="field-label">📋 Mission Report (Gather)</div>
              <div class="field-value-box text-ellipsis font-mono" id="rank-${r}-report" title="No report assembled">
                <span class="placeholder-muted">Pending dispatch</span>
              </div>
            </div>
          </div>

          <div class="rank-box-footer">
            <span class="status-indicator-dot" id="rank-${r}-dot"></span>
            <span class="status-indicator-text" id="rank-${r}-status">Ready (IDLE)</span>
          </div>
        </div>
      `;
    }

    rowContainer.innerHTML = html;
  }

  /**
   * Draws dynamic SVG connection lines and arrow markers between Command Centre (Root)
   * and every worker rank.
   */
  function drawConnectionArrows() {
    const svg = document.getElementById('comm-svg-overlay');
    const container = document.getElementById('comm-canvas-wrapper');
    if (!svg || !container) return;

    const rootBox = document.getElementById(`rank-box-${currentRootRank}`);
    if (!rootBox) return;

    const containerRect = container.getBoundingClientRect();
    svg.setAttribute('width', containerRect.width);
    svg.setAttribute('height', containerRect.height);

    const rootRect = rootBox.getBoundingClientRect();
    const rootX = rootRect.left - containerRect.left + (rootRect.width / 2);
    const rootY = rootRect.bottom - containerRect.top;

    let svgContent = `
      <defs>
        <marker id="arrow-down" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
          <path d="M 0 1 L 8 5 L 0 9 z" fill="#3b82f6" />
        </marker>
        <marker id="arrow-up" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
          <path d="M 0 1 L 8 5 L 0 9 z" fill="#10b981" />
        </marker>
        <linearGradient id="bcastGrad" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stop-color="#8b5cf6" stop-opacity="0.8"/>
          <stop offset="100%" stop-color="#3b82f6" stop-opacity="0.8"/>
        </linearGradient>
      </defs>
    `;

    for (let r = 0; r < currentProcesses; r++) {
      if (r === currentRootRank) continue;

      const workerBox = document.getElementById(`rank-box-${r}`);
      if (!workerBox) continue;

      const workerRect = workerBox.getBoundingClientRect();
      const workerX = workerRect.left - containerRect.left + (workerRect.width / 2);
      const workerY = workerRect.bottom - containerRect.top;

      // Draw curved bezier line between root and worker
      const ctrlY = Math.max(rootY, workerY) + 36;
      const pathData = `M ${rootX} ${rootY} Q ${(rootX + workerX) / 2} ${ctrlY} ${workerX} ${workerY}`;

      svgContent += `
        <path id="arrow-path-${r}" class="comm-arrow-path" d="${pathData}"
              stroke="url(#bcastGrad)" stroke-width="2" fill="none" stroke-dasharray="6,4" />
      `;
    }

    svg.innerHTML = svgContent;
  }

  /**
   * Animates communication flows (Bcast, Scatter, Gather) with particle pulse animations.
   */
  function animateOperationFlow(operation, result) {
    const svg = document.getElementById('comm-svg-overlay');
    if (!svg) return;

    if (currentAnimationTimer) {
      clearTimeout(currentAnimationTimer);
    }

    // Add active animation class to paths
    const paths = svg.querySelectorAll('.comm-arrow-path');
    paths.forEach(p => {
      p.classList.remove('anim-bcast', 'anim-scatter', 'anim-gather');
      void p.offsetWidth; // trigger reflow

      if (operation === 'BCAST') {
        p.classList.add('anim-bcast');
      } else if (operation === 'SCATTER') {
        p.classList.add('anim-scatter');
      } else if (operation === 'GATHER') {
        p.classList.add('anim-gather');
      } else { // RUN_ALL: sequentially trigger
        p.classList.add('anim-bcast');
      }
    });

    const banner = document.getElementById('flow-status-banner');
    if (banner) {
      banner.style.display = 'flex';
      banner.className = 'flow-banner complete';
      banner.innerHTML = `✅ <strong>${operation} Completed:</strong> All ${result.processes || result.numProcesses} ranks synchronized at cyclic barriers.`;
      currentAnimationTimer = setTimeout(() => {
        banner.style.display = 'none';
      }, 7000);
    }
  }

  /**
   * Updates all UI elements with results from the server or mock generator.
   */
  function renderResult(result) {
    const processes = result.processes || result.numProcesses || currentProcesses;
    const root = result.rootRank !== undefined ? result.rootRank : currentRootRank;
    const chunkSize = result.chunkSize || currentChunkSize;

    // 1. Update verification & telemetry metrics
    const durationEl = document.getElementById('metric-duration');
    const selfCheckBadge = document.getElementById('badge-verification');
    const checksumText = document.getElementById('checksum-text');
    const gatheredBufferEl = document.getElementById('gathered-buffer');
    const scatteredBufferEl = document.getElementById('scattered-buffer');

    if (durationEl) durationEl.textContent = `${result.durationMs || 10} ms`;

    if (selfCheckBadge && checksumText) {
      if (result.selfCheckPassed || result.verified) {
        selfCheckBadge.className = 'badge badge-green pulse-glow';
        selfCheckBadge.innerHTML = '🛡️ SELF-CHECK PASSED';
        checksumText.innerHTML = `<span style="color: #10b981; font-weight: 700;">VERIFIED INTEGRITY:</span> All ${processes * chunkSize} rescue reports strictly ordered and uncorrupted.`;
      } else {
        selfCheckBadge.className = 'badge badge-info';
        selfCheckBadge.textContent = `${result.operation || 'STEP EXECUTED'}`;
        checksumText.innerHTML = `Operation completed. Ready for next collective primitive.`;
      }
    }

    if (gatheredBufferEl) {
      if (result.gathered && result.gathered.length > 0) {
        gatheredBufferEl.innerHTML = result.gathered.map(r => `<span class="buffer-chip chip-green">${escapeHtml(r)}</span>`).join(' ');
      } else if (result.gatheredData && result.gatheredData.length > 0) {
        gatheredBufferEl.textContent = JSON.stringify(result.gatheredData);
      } else {
        gatheredBufferEl.innerHTML = '<span class="text-muted">[ Awaiting Gather ]</span>';
      }
    }

    if (scatteredBufferEl) {
      if (result.scattered && result.scattered.length > 0) {
        scatteredBufferEl.innerHTML = result.scattered.map(z => `<span class="buffer-chip chip-blue">${escapeHtml(z)}</span>`).join(' ');
      } else {
        scatteredBufferEl.innerHTML = '<span class="text-muted">[ Awaiting Scatter ]</span>';
      }
    }

    // 2. Update Rank Cards
    renderRankRow(processes, root);
    drawConnectionArrows();

    // Fill in values for each rank
    for (let r = 0; r < processes; r++) {
      const isRoot = (r === root);
      const alertEl = document.getElementById(`rank-${r}-alert`);
      const zonesEl = document.getElementById(`rank-${r}-zones`);
      const reportEl = document.getElementById(`rank-${r}-report`);
      const dotEl = document.getElementById(`rank-${r}-dot`);
      const statusEl = document.getElementById(`rank-${r}-status`);

      // Alert
      if (alertEl) {
        if (result.alertText) {
          alertEl.textContent = result.alertText;
          alertEl.classList.add('highlight-update');
        } else if (result.alertData) {
          alertEl.textContent = JSON.stringify(result.alertData);
        }
      }

      // Scatter Zones
      if (zonesEl) {
        if (result.scattered && result.scattered.length >= processes * chunkSize) {
          const myZones = result.scattered.slice(r * chunkSize, (r + 1) * chunkSize);
          zonesEl.innerHTML = myZones.map(z => `<span class="zone-badge">${escapeHtml(z)}</span>`).join(' ');
        }
      }

      // Gather Report
      if (reportEl) {
        if (result.gathered && result.gathered.length >= processes * chunkSize) {
          const myReports = result.gathered.slice(r * chunkSize, (r + 1) * chunkSize);
          reportEl.textContent = myReports.join(', ');
        }
      }

      // Status Indicator
      if (dotEl && statusEl) {
        dotEl.className = 'status-indicator-dot dot-active';
        statusEl.textContent = isRoot ? 'Command Sync (OK)' : 'Mission Verified (OK)';
      }
    }

    // 3. Render Per-Rank Timeline Table
    renderTimelineTable(result.timeline);

    // 4. Render Step Trace Terminal Log
    renderTraceLog(result.steps);
  }

  function renderTimelineTable(timeline) {
    const tableBody = document.getElementById('timeline-table-body');
    if (!tableBody) return;

    if (!Array.isArray(timeline) || timeline.length === 0) {
      tableBody.innerHTML = `<tr><td colspan="4" style="text-align:center; color:#64748b; padding:16px;">No timeline recorded. Run a simulation above.</td></tr>`;
      return;
    }

    const actionColors = {
      'BCAST_DISPATCHED': 'badge-purple',
      'BCAST_RECEIVED': 'badge-blue',
      'SCATTER_DISPATCHED': 'badge-purple',
      'SCATTER_RECEIVED': 'badge-blue',
      'LOCAL_MISSION_COMPLETED': 'badge-green',
      'GATHER_SENT': 'badge-orange',
      'GATHER_ASSEMBLED': 'badge-green'
    };

    tableBody.innerHTML = timeline.map(entry => {
      const color = actionColors[entry.action] || 'badge-gray';
      const cleanData = typeof entry.data === 'object' ? JSON.stringify(entry.data) : String(entry.data);
      return `
        <tr>
          <td style="font-weight: 700; color: #475569;">#${entry.order}</td>
          <td><span class="badge ${entry.rank === currentRootRank ? 'badge-purple' : 'badge-gray'}">Rank ${entry.rank}</span></td>
          <td><span class="badge ${color}">${escapeHtml(entry.action)}</span></td>
          <td class="font-mono text-ellipsis" style="max-width: 380px;" title="${escapeHtml(cleanData)}">${escapeHtml(cleanData)}</td>
        </tr>
      `;
    }).join('');
  }

  function renderTraceLog(steps) {
    const traceContainer = document.getElementById('comm-trace-log');
    if (!traceContainer) return;

    if (!Array.isArray(steps) || steps.length === 0) {
      traceContainer.innerHTML = '<div style="color: #64748b; font-style: italic;">Awaiting collective communication execution...</div>';
      return;
    }

    traceContainer.innerHTML = steps.map((st, i) => {
      return `
        <div class="step-row">
          <span class="step-idx">${i + 1}</span>
          <span class="step-text">${escapeHtml(st)}</span>
        </div>
      `;
    }).join('');
    traceContainer.scrollTop = traceContainer.scrollHeight;
  }

  /**
   * Realistic Mock Data Generator for offline or ?mock=1 testing.
   */
  function generateMockResult(opType, req) {
    const p = req.processes || 4;
    const c = req.chunkSize || 2;
    const root = req.rootRank || 0;
    const alert = req.alertText || ALERT_PRESETS[0];

    const zones = [];
    for (let i = 0; i < p * c; i++) {
      const rk = Math.floor(i / c);
      const sub = (i % c) + 1;
      zones.push(`ZONE-${String.fromCharCode(65 + (rk % 26))}${sub}`);
    }

    const gathered = [];
    for (let r = 0; r < p; r++) {
      for (let j = 0; j < c; j++) {
        gathered.push(`REPORT[R${r}:${zones[r * c + j]}:EVAC_COMPLETE]`);
      }
    }

    const timeline = [];
    let order = 1;
    timeline.push({ rank: root, action: 'BCAST_DISPATCHED', data: alert, order: order++ });
    for (let r = 0; r < p; r++) {
      timeline.push({ rank: r, action: 'BCAST_RECEIVED', data: alert, order: order++ });
    }
    timeline.push({ rank: root, action: 'SCATTER_DISPATCHED', data: JSON.stringify(zones), order: order++ });
    for (let r = 0; r < p; r++) {
      const myZ = zones.slice(r * c, (r + 1) * c);
      timeline.push({ rank: r, action: 'SCATTER_RECEIVED', data: JSON.stringify(myZ), order: order++ });
      timeline.push({ rank: r, action: 'LOCAL_MISSION_COMPLETED', data: `[Deliveries Finished]`, order: order++ });
      timeline.push({ rank: r, action: 'GATHER_SENT', data: `[Reports Transmitted]`, order: order++ });
    }
    timeline.push({ rank: root, action: 'GATHER_ASSEMBLED', data: JSON.stringify(gathered), order: order++ });

    const steps = [
      `=== STEP 1: BROADCAST (MPI_Bcast) ===`,
      `[Command Centre (Rank ${root})] Broadcasting alert: "${alert}"`,
      `=== STEP 2: SCATTER (MPI_Scatter) ===`,
      `[Command Centre] Scattering ${zones.length} disaster zones across ${p} rescue teams.`,
      `=== STEP 3: GATHER (MPI_Gather) ===`,
      `Command Centre gathering mission reports from all rescue teams in rank order.`,
      `=== STEP 4: SELF-CHECK VERIFICATION ===`,
      `Command Centre assembled reports: ${JSON.stringify(gathered)}`,
      `Strict rank-order & non-corruption check: PASSED (VERIFIED)`
    ];

    return {
      operation: opType,
      processes: p,
      numProcesses: p,
      chunkSize: c,
      rootRank: root,
      alertText: alert,
      scattered: zones,
      gathered: gathered,
      selfCheckPassed: true,
      verified: true,
      durationMs: Math.floor(Math.random() * 8) + 6,
      timeline: timeline,
      steps: steps
    };
  }

  function escapeHtml(str) {
    if (typeof str !== 'string') return String(str);
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
      const later = () => {
        clearTimeout(timeout);
        func(...args);
      };
      clearTimeout(timeout);
      timeout = setTimeout(later, wait);
    };
  }

})();
