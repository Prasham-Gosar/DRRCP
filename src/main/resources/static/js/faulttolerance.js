/**
 * faulttolerance.js — Frontend logic for Exp 8: Primary-Backup Fault Tolerance.
 * Author: Grasit (Consensus & Fault Tolerance)
 */

(function () {
  'use strict';

  let state = {
    primaryId: 'Coordinator-Primary',
    backupId: 'Coordinator-Backup',
    primaryActive: true,
    backupPromoted: false,
    primaryState: {},
    backupState: {},
    currentState: {},
    steps: []
  };

  // ── Render Topology Diagram ──
  function renderDiagram() {
    const diagram = document.getElementById('ft-diagram');
    if (!diagram) return;

    const primaryClass = state.primaryActive ? 'primary-active' : 'primary-failed';
    const backupClass = state.backupPromoted ? 'backup-promoted' : 'backup-standby';
    const arrowClass = state.primaryActive ? '' : ' broken';
    const primaryStatusColor = state.primaryActive
      ? 'background: #ecfdf5; color: #065f46;'
      : 'background: #fee2e2; color: #991b1b;';
    const backupStatusColor = state.backupPromoted
      ? 'background: #fef3c7; color: #92400e;'
      : 'background: #f1f5f9; color: #64748b;';

    diagram.innerHTML = `
      <div class="ft-node-box ${primaryClass}">
        <div class="ft-node-label" style="color: #64748b;">Primary Coordinator</div>
        <div class="ft-node-id">${state.primaryId}</div>
        <div class="ft-node-status" style="${primaryStatusColor}">
          ${state.primaryActive ? '✅ ACTIVE LEADER' : '❌ OFFLINE (FAILED)'}
        </div>
      </div>

      <div class="ft-arrow${arrowClass}">
        <span style="font-weight:600;">Replication</span>
        <span class="ft-arrow-line">${state.primaryActive ? '→→→' : '✕✕✕'}</span>
        <span style="font-size:0.75rem;">${state.primaryActive ? 'Synchronous ACK' : 'Link Broken'}</span>
      </div>

      <div class="ft-node-box ${backupClass}">
        <div class="ft-node-label" style="color: #64748b;">Hot Standby Replica</div>
        <div class="ft-node-id">${state.backupId}</div>
        <div class="ft-node-status" style="${backupStatusColor}">
          ${state.backupPromoted ? '👑 ACTIVE PRIMARY' : '🛡️ HOT STANDBY'}
        </div>
      </div>
    `;
  }

  // ── Render KPIs ──
  function renderKpis() {
    const pState = state.primaryState || {};
    const bState = state.backupState || state.currentState || {};
    const updateCount = Object.keys(bState).length;

    const pEl = document.getElementById('kpi-primary');
    const bEl = document.getElementById('kpi-backup');
    const uEl = document.getElementById('kpi-updates');
    const pBadge = document.getElementById('kpi-primary-status');
    const bBadge = document.getElementById('kpi-backup-status');
    const healthEl = document.getElementById('kpi-health');
    const healthBadge = document.getElementById('kpi-health-badge');

    if (pEl) pEl.textContent = state.primaryId;
    if (bEl) bEl.textContent = state.backupId;
    if (uEl) uEl.textContent = updateCount;

    if (pBadge) {
      if (state.primaryActive) {
        pBadge.textContent = 'ACTIVE';
        pBadge.className = 'badge badge-green';
      } else {
        pBadge.textContent = 'FAILED';
        pBadge.className = 'badge badge-red';
      }
    }

    if (bBadge) {
      if (state.backupPromoted) {
        bBadge.textContent = 'PROMOTED LEADER';
        bBadge.className = 'badge badge-amber';
      } else {
        bBadge.textContent = 'STANDBY';
        bBadge.className = 'badge badge-gray';
      }
    }

    if (healthEl && healthBadge) {
      if (state.primaryActive) {
        healthEl.textContent = 'HEALTHY';
        healthBadge.textContent = 'SYNCHRONIZED';
        healthBadge.className = 'badge badge-green';
      } else if (state.backupPromoted) {
        healthEl.textContent = 'FAILOVER';
        healthBadge.textContent = 'BACKUP PROMOTED';
        healthBadge.className = 'badge badge-amber';
      } else {
        healthEl.textContent = 'CRITICAL';
        healthBadge.textContent = 'ALL REPLICAS DOWN';
        healthBadge.className = 'badge badge-red';
      }
    }

    const btnFail = document.getElementById('btn-fail-primary');
    if (btnFail) btnFail.disabled = !state.primaryActive;
  }

  // ── Render Primary & Backup Replica State Tables ──
  function renderReplicaTables() {
    const pBody = document.getElementById('primary-table-body');
    const bBody = document.getElementById('backup-table-body');
    const pTitle = document.getElementById('primary-card-title');
    const bTitle = document.getElementById('backup-card-title');
    const pBadge = document.getElementById('primary-card-badge');
    const bBadge = document.getElementById('backup-card-badge');

    if (pTitle) pTitle.textContent = `🖥️ Primary: ${state.primaryId}`;
    if (bTitle) bTitle.textContent = `🛡️ Backup: ${state.backupId}`;

    if (pBadge) {
      pBadge.textContent = state.primaryActive ? 'ACTIVE' : 'FAILED';
      pBadge.className = `badge ${state.primaryActive ? 'badge-green' : 'badge-red'}`;
    }
    if (bBadge) {
      bBadge.textContent = state.backupPromoted ? 'ACTIVE PRIMARY' : 'STANDBY';
      bBadge.className = `badge ${state.backupPromoted ? 'badge-amber' : 'badge-gray'}`;
    }

    const pEntries = Object.values(state.primaryState || {});
    const bEntries = Object.values(state.backupState || state.currentState || {});

    if (pBody) {
      if (pEntries.length === 0) {
        pBody.innerHTML = '<tr><td colspan="4" style="text-align:center; color:#94a3b8; padding:16px; font-style:italic;">No updates in primary.</td></tr>';
      } else {
        pBody.innerHTML = pEntries.map(u => `
          <tr>
            <td><strong>${u.resourceId}</strong></td>
            <td><small>${u.updateType}</small></td>
            <td>${renderValueBadge(u.value)}</td>
            <td><small>${u.sourceNode}</small></td>
          </tr>
        `).join('');
      }
    }

    if (bBody) {
      if (bEntries.length === 0) {
        bBody.innerHTML = '<tr><td colspan="4" style="text-align:center; color:#94a3b8; padding:16px; font-style:italic;">No updates replicated yet.</td></tr>';
      } else {
        bBody.innerHTML = bEntries.map(u => `
          <tr>
            <td><strong>${u.resourceId}</strong></td>
            <td><small>${u.updateType}</small></td>
            <td>${renderValueBadge(u.value)}</td>
            <td><small>${u.sourceNode}</small></td>
          </tr>
        `).join('');
      }
    }
  }

  // ── Render Diff Verification View ──
  function renderDiffTable() {
    const diffBody = document.getElementById('diff-table-body');
    const diffBadge = document.getElementById('diff-status-badge');
    if (!diffBody) return;

    const pMap = state.primaryState || {};
    const bMap = state.backupState || state.currentState || {};

    const allKeys = Array.from(new Set([...Object.keys(pMap), ...Object.keys(bMap)]));
    if (allKeys.length === 0) {
      diffBody.innerHTML = '<tr><td colspan="4" style="text-align:center; color:#94a3b8; padding:16px; font-style:italic;">No updates in system.</td></tr>';
      if (diffBadge) {
        diffBadge.textContent = 'SYNCHRONIZED (0 DIFF)';
        diffBadge.className = 'badge badge-green';
      }
      return;
    }

    let diffCount = 0;
    diffBody.innerHTML = allKeys.map(k => {
      const pVal = pMap[k] ? pMap[k].value : null;
      const bVal = bMap[k] ? bMap[k].value : null;

      let statusBadge = '';
      if (pVal !== null && bVal !== null && pVal === bVal) {
        statusBadge = '<span class="badge badge-green">✅ SYNCHRONIZED</span>';
      } else if (pVal !== null && bVal === null) {
        diffCount++;
        statusBadge = '<span class="badge badge-red">⚠️ MISSING ON BACKUP</span>';
      } else if (pVal === null && bVal !== null) {
        statusBadge = '<span class="badge badge-info">👑 POST-FAILOVER WRITE</span>';
      } else {
        diffCount++;
        statusBadge = '<span class="badge badge-amber">⚡ VALUE DIVERGENCE</span>';
      }

      return `
        <tr>
          <td><strong>${k}</strong></td>
          <td>${pVal !== null ? renderValueBadge(pVal) : '<span style="color:#94a3b8; font-style:italic;">None</span>'}</td>
          <td>${bVal !== null ? renderValueBadge(bVal) : '<span style="color:#94a3b8; font-style:italic;">None</span>'}</td>
          <td>${statusBadge}</td>
        </tr>
      `;
    }).join('');

    if (diffBadge) {
      if (diffCount === 0) {
        diffBadge.textContent = 'SYNCHRONIZED (0 MISSING)';
        diffBadge.className = 'badge badge-green';
      } else {
        diffBadge.textContent = `DIFF DETECTED (${diffCount} DISCREPANCIES)`;
        diffBadge.className = 'badge badge-red';
      }
    }
  }

  // ── Badge Helper ──
  function renderValueBadge(val) {
    if (val === 'NEEDS_RESCUE') return '<span class="badge badge-red">NEEDS_RESCUE</span>';
    if (val === 'RESCUED') return '<span class="badge badge-green">RESCUED</span>';
    if (val === 'OPEN') return '<span class="badge badge-green">OPEN</span>';
    if (val === 'BLOCKED') return '<span class="badge badge-red">BLOCKED</span>';
    if (val === 'DELIVERED') return '<span class="badge badge-info">DELIVERED</span>';
    return `<span class="badge badge-gray">${val}</span>`;
  }

  // ── Append Trace ──
  function appendTrace(steps) {
    const container = document.getElementById('trace-container');
    if (!container) return;
    if (container.querySelector('[style*="italic"]')) {
      container.innerHTML = '';
    }
    (steps || []).forEach(s => {
      const div = document.createElement('div');
      div.style.cssText = 'padding: 3px 6px; font-size: 0.78rem; line-height: 1.5;';
      if (s.includes('FAILED') || s.includes('Cannot')) {
        div.style.color = '#fca5a5';
      } else if (s.includes('PROMOTED') || s.includes('promoted')) {
        div.style.color = '#fde68a';
      } else if (s.includes('replicated') || s.includes('replicate') || s.includes('Current System State')) {
        div.style.color = '#86efac';
      } else {
        div.style.color = '#93c5fd';
      }
      div.textContent = `> ${s}`;
      container.appendChild(div);
    });
    container.scrollTop = container.scrollHeight;
  }

  // ── Update All UI ──
  function updateUI(res) {
    if (!res) return;
    state = res;
    renderDiagram();
    renderKpis();
    renderReplicaTables();
    renderDiffTable();
    if (res.steps) appendTrace(res.steps);
  }

  // ── Load State ──
  async function loadState() {
    try {
      const res = await api.get('/api/ft/state', {
        mockUrl: '/mock/ft_state.json'
      });
      updateUI(res);
    } catch (e) {
      console.warn('Could not load FT state:', e);
      renderDiagram();
      renderKpis();
    }
  }

  // ── Send Update ──
  async function sendUpdate() {
    const resourceId = document.getElementById('resource-id').value.trim();
    if (!resourceId) {
      if (window.toast) window.toast.warning('Resource ID is required.');
      return;
    }

    try {
      const res = await api.post('/api/ft/updates', {
        resourceId: resourceId,
        updateType: document.getElementById('update-type').value,
        value: document.getElementById('update-value').value,
        sourceNode: document.getElementById('source-node').value
      }, {
        mockUrl: '/mock/ft_state.json'
      });
      updateUI(res);
      if (window.toast) window.toast.success(`Update ${resourceId} synchronously replicated!`);
    } catch (e) {
      console.error('Update error:', e);
      if (window.toast) window.toast.error('Failed to replicate update.');
    }
  }

  // ── Fail Primary ──
  async function failPrimary() {
    try {
      const res = await api.post('/api/ft/primary/fail', {}, {
        mockUrl: '/mock/ft_state.json'
      });
      updateUI(res);
      if (window.toast) window.toast.warning('💥 Primary coordinator failed! Backup promoted to primary.');
    } catch (e) {
      console.error('Fail primary error:', e);
      if (window.toast) window.toast.error('Failed to simulate failure.');
    }
  }

  // ── Reset System ──
  async function resetSystem() {
    try {
      const res = await api.post('/api/ft/reset', {}, {
        mockUrl: '/mock/ft_state.json'
      });
      document.getElementById('trace-container').innerHTML =
        '<div style="color: #64748b; font-style: italic;">Send an update or trigger failure to see replication trace.</div>';
      updateUI(res);
      if (window.toast) window.toast.info('Fault-tolerance subsystem reset.');
    } catch (e) {
      console.error('Reset error:', e);
      if (window.toast) window.toast.error('Failed to reset system.');
    }
  }

  // ── Run Full Demo ──
  async function runFullDemo() {
    try {
      if (window.toast) window.toast.info('Running automated failover demo...');
      await resetSystem();

      // Step 1: Send House-12
      await api.post('/api/ft/updates', {
        resourceId: 'House-12', updateType: 'STATUS', value: 'NEEDS_RESCUE', sourceNode: 'UAV-01'
      });
      await loadState();

      // Step 2: Send Hospital-01
      await api.post('/api/ft/updates', {
        resourceId: 'Hospital-01', updateType: 'SUPPLIES', value: 'DELIVERED', sourceNode: 'Edge-01'
      });
      await loadState();

      // Step 3: Fail primary
      await new Promise(r => setTimeout(r, 600));
      await failPrimary();

      // Step 4: Send update after failure to promoted backup
      await new Promise(r => setTimeout(r, 600));
      const finalRes = await api.post('/api/ft/updates', {
        resourceId: 'Bridge-4', updateType: 'STATUS', value: 'OPEN', sourceNode: 'UAV-02'
      });
      updateUI(finalRes);

      if (window.toast) window.toast.success('🎉 Demo complete! Backup accepted post-failover updates with zero data loss.');
    } catch (e) {
      console.error('Demo error:', e);
      if (window.toast) window.toast.error('Demo encountered an error.');
    }
  }

  // ── Document Ready ──
  document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('update-form')?.addEventListener('submit', sendUpdate);
    document.getElementById('btn-send-update')?.addEventListener('click', sendUpdate);
    document.getElementById('btn-fail-primary')?.addEventListener('click', failPrimary);
    document.getElementById('btn-reset')?.addEventListener('click', resetSystem);
    document.getElementById('btn-run-demo')?.addEventListener('click', runFullDemo);
    document.getElementById('btn-clear-trace')?.addEventListener('click', () => {
      document.getElementById('trace-container').innerHTML =
        '<div style="color: #64748b; font-style: italic;">Trace cleared.</div>';
    });

    if (window.events) {
      events.createLiveLog('module-live-log', { maxEntries: 50 });
      events.on('ft', 'FT_PRIMARY_FAILED', () => loadState());
      events.on('ft', 'FT_BACKUP_PROMOTED', () => loadState());
      events.on('ft', 'FT_REPLICATED', () => loadState());
    }

    loadState();
  });

})();

