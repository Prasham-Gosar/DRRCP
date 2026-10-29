/**
 * scenario.js — Frontend orchestrator for the Full Story Scenario Runner
 * Author: Prasham (own page per ownership rules)
 */

(function () {
  'use strict';

  // ── DOM References ──
  const btnRun            = document.getElementById('btn-run');
  const btnReset          = document.getElementById('btn-reset');
  const btnClearTrace     = document.getElementById('btn-clear-trace');
  const traceEl           = document.getElementById('scenario-trace');
  const chkFastMode       = document.getElementById('cfg-fast-mode');

  const heroStateBadge    = document.getElementById('hero-state-badge');
  const heroTitle         = document.getElementById('hero-title');
  const heroSub           = document.getElementById('hero-sub');
  const heroDuration      = document.getElementById('hero-duration');

  const reconcileCard     = document.getElementById('reconcile-card');
  const reconcileTbody    = document.getElementById('reconcile-tbody');
  const recTotalBadge     = document.getElementById('rec-total-badge');
  const recConflictsBadge = document.getElementById('rec-conflicts-badge');

  // ── State ──
  let timerInterval = null;
  let startTime = 0;
  let isRunning = false;

  // ── Init ──
  document.addEventListener('DOMContentLoaded', () => {
    initLiveLog();
    subscribeToEvents();
    pollStatus();

    btnRun.addEventListener('click', startScenario);
    btnReset.addEventListener('click', resetScenario);
    btnClearTrace.addEventListener('click', () => {
      traceEl.innerHTML = '<div style="color:#64748b;">Trace cleared.</div>';
    });
  });

  // ── Stepper Management ──

  function setStepState(phaseNum, state, summaryText) {
    const card = document.getElementById(`step-card-${phaseNum}`);
    const badge = document.getElementById(`step-badge-${phaseNum}`);
    const summary = document.getElementById(`step-summary-${phaseNum}`);
    if (!card || !badge) return;

    card.classList.remove('active', 'completed', 'failed');

    if (state === 'RUNNING') {
      card.classList.add('active');
      badge.className = 'badge badge-blue';
      badge.textContent = 'RUNNING';
    } else if (state === 'COMPLETED') {
      card.classList.add('completed');
      badge.className = 'badge badge-green';
      badge.textContent = 'DONE';
    } else if (state === 'FAILED') {
      card.classList.add('failed');
      badge.className = 'badge badge-red';
      badge.textContent = 'FAILED';
    } else {
      badge.className = 'badge badge-gray';
      badge.textContent = 'PENDING';
    }

    if (summaryText && summary) {
      summary.textContent = summaryText;
    }
  }

  function resetAllSteps() {
    for (let i = 1; i <= 7; i++) {
      setStepState(i, 'PENDING');
    }
    reconcileCard.style.display = 'none';
    heroStateBadge.className = 'badge badge-gray';
    heroStateBadge.textContent = 'IDLE';
    heroTitle.textContent = 'Ready to execute full coordination story';
    heroSub.textContent = 'Click Run to begin Phase 1 through 7.';
    heroDuration.textContent = '0.0s';
    btnRun.disabled = false;
    stopTimer();
  }

  // ── Scenario Execution ──

  async function startScenario() {
    btnRun.disabled = true;
    isRunning = true;
    startTime = Date.now();
    startTimer();

    heroStateBadge.className = 'badge badge-blue';
    heroStateBadge.textContent = 'RUNNING';
    heroTitle.textContent = 'Executing full disaster relief scenario pipeline...';

    traceEl.innerHTML = '';
    appendTrace('[Runner] Starting scenario execution on server...');

    const payload = {
      fastMode: chkFastMode.checked,
      partitionDurationMs: chkFastMode.checked ? 0 : 2000
    };

    try {
      await api.post('/api/scenario/run', payload);
      toast.info('Scenario Started', 'Running Phase 1 (Boot & Register)...');
      // Start polling to track real-time progress alongside SSE
      startPolling();
    } catch (err) {
      isRunning = false;
      btnRun.disabled = false;
      stopTimer();
      heroStateBadge.className = 'badge badge-red';
      heroStateBadge.textContent = 'FAILED';
      toast.error('Failed to start scenario', err.message);
      appendTrace('[Runner] ERROR: ' + err.message);
    }
  }

  async function resetScenario() {
    try {
      await api.post('/api/scenario/reset');
      resetAllSteps();
      toast.info('Scenario Reset', 'All states cleared.');
    } catch (err) {
      toast.error('Reset Failed', err.message);
    }
  }

  // ── Polling & Status Sync ──

  let pollTimer = null;
  function startPolling() {
    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(async () => {
      await pollStatus();
    }, 1000);
  }

  async function pollStatus() {
    try {
      const data = await api.get('/api/scenario/status');
      updateFromStatus(data);
      if (!data.running && pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
      }
    } catch (_) { }
  }

  function updateFromStatus(status) {
    if (!status) return;

    if (status.running) {
      isRunning = true;
      btnRun.disabled = true;
      heroStateBadge.className = 'badge badge-blue';
      heroStateBadge.textContent = 'RUNNING: Phase ' + status.currentPhase;
    } else if (status.state === 'COMPLETED') {
      isRunning = false;
      btnRun.disabled = false;
      stopTimer();
      heroStateBadge.className = 'badge badge-green';
      heroStateBadge.textContent = 'COMPLETED';
      heroTitle.textContent = 'All 7 Phases Executed Successfully!';
      heroSub.textContent = `Completed in ${((status.totalDurationMs || 0) / 1000).toFixed(1)}s. Full disaster pipeline verified.`;
      if (status.totalDurationMs) {
        heroDuration.textContent = ((status.totalDurationMs / 1000).toFixed(1)) + 's';
      }
      fetchReconciliationSummary(status.reconciliation);
    } else if (status.state === 'FAILED') {
      isRunning = false;
      btnRun.disabled = false;
      stopTimer();
      heroStateBadge.className = 'badge badge-red';
      heroStateBadge.textContent = 'FAILED';
      heroTitle.textContent = 'Scenario Failed';
      heroSub.textContent = status.errorMessage || 'An error occurred during execution.';
    }

    // Update phase details
    if (Array.isArray(status.phases)) {
      status.phases.forEach(p => {
        setStepState(p.phaseNumber, p.status, p.summary);
      });
    }
  }

  // ── Reconciliation Results Display ──

  async function fetchReconciliationSummary(fallbackData) {
    try {
      let rec = null;
      // Try fetching latest reconciliation endpoint first
      try {
        rec = await api.get('/api/reconcile/latest');
      } catch (_) { }

      // Fallback to data attached directly to scenario status
      if (!rec || !rec.resolutions || rec.resolutions.length === 0) {
        rec = fallbackData;
      }

      if (rec && Array.isArray(rec.resolutions) && rec.resolutions.length > 0) {
        renderReconciliationTable(rec);
      }
    } catch (e) {
      console.warn('Could not load reconciliation summary:', e);
    }
  }

  function renderReconciliationTable(rec) {
    reconcileCard.style.display = 'block';
    recTotalBadge.textContent = `${rec.totalUpdates} Updates Reconciled`;
    recConflictsBadge.textContent = `${rec.conflictsDetected} Conflict Resolved`;

    reconcileTbody.innerHTML = rec.resolutions.map(r => {
      const isConflict = r.needsHumanReview;
      const reviewBadge = isConflict
        ? '<span class="badge badge-amber">FLAGGED FOR HUMAN REVIEW</span>'
        : '<span class="badge badge-green">AUTO-RESOLVED</span>';

      const methodBadge = r.method === 'POLICY_RESOLVED'
        ? '<span class="badge badge-red">POLICY RESOLVED</span>'
        : '<span class="badge badge-blue">CRDT MERGED</span>';

      return `
        <tr>
          <td><strong>${escapeHtml(r.resourceKey)}</strong></td>
          <td><code>${escapeHtml(r.resourceType)}</code></td>
          <td><strong style="color:${r.finalValue === 'NEEDS_RESCUE' ? '#ef4444' : '#10b981'}; font-size:0.95rem;">${escapeHtml(r.finalValue)}</strong></td>
          <td>${methodBadge}</td>
          <td>${reviewBadge}</td>
          <td style="font-size:0.82rem; color:#475569;">${escapeHtml(r.explanation || '—')}</td>
        </tr>`;
    }).join('');
  }

  // ── SSE Event Listener ──

  function subscribeToEvents() {
    if (!window.events) return;

    events.on('core', 'SCENARIO_PHASE', (payload) => {
      const p = payload.phase;
      const pStatus = payload.phaseStatus;
      const msg = payload.message || '';

      appendTrace(`[Phase ${p}: ${payload.name}] ${pStatus} — ${msg}`);

      if (pStatus === 'START' || pStatus === 'RUNNING') {
        setStepState(p, 'RUNNING');
        heroTitle.textContent = `Executing Phase ${p}: ${payload.name}...`;
        heroSub.textContent = msg;
      } else if (pStatus === 'COMPLETED') {
        setStepState(p, 'COMPLETED', msg);
        toast.success(`Phase ${p} Done`, msg);
      } else if (pStatus === 'FAILED') {
        setStepState(p, 'FAILED', msg);
        toast.error(`Phase ${p} Failed`, msg);
      }

      if (p === 7 && pStatus === 'COMPLETED') {
        pollStatus();
      }
    });

    events.on('consensus', 'RECONCILED', (rec) => {
      renderReconciliationTable(rec);
    });
  }

  // ── Helpers ──

  function startTimer() {
    stopTimer();
    timerInterval = setInterval(() => {
      const elapsed = (Date.now() - startTime) / 1000;
      heroDuration.textContent = elapsed.toFixed(1) + 's';
    }, 100);
  }

  function stopTimer() {
    if (timerInterval) {
      clearInterval(timerInterval);
      timerInterval = null;
    }
  }

  function appendTrace(text) {
    const now = new Date().toLocaleTimeString();
    const row = document.createElement('div');
    row.className = 'step-row';
    row.innerHTML = `
      <span class="log-time">${now}</span>
      <span class="step-text">${escapeHtml(text)}</span>`;
    traceEl.appendChild(row);
    traceEl.scrollTop = traceEl.scrollHeight;
  }

  function initLiveLog() {
    if (window.events && typeof events.createLiveLog === 'function') {
      events.createLiveLog('scenario-live-log', {
        maxEntries: 60,
        filterModule: 'core'
      });
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }
})();

