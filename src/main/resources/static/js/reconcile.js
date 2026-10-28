/**
 * reconcile.js — Frontend logic for Exp 5: Reconciliation & CRDT Merge.
 * Author: Grasit (Consensus & Fault Tolerance)
 */

(function () {
  'use strict';

  let currentUpdates = [];
  let latestReconciliation = null;

  // ── Format Vector Clock ──
  function formatVectorClock(vc) {
    if (!vc || Object.keys(vc).length === 0) return '<span class="vc-badge">[empty]</span>';
    const parts = Object.entries(vc).map(([node, count]) => {
      if (count > 0) {
        return `<span class="tick">${node}: ${count}</span>`;
      }
      return `${node}: 0`;
    });
    return `<span class="vc-badge">${parts.join(', ')}</span>`;
  }

  // ── Parse SET_ADD Items into Chips ──
  function renderValueChips(type, val) {
    if (type === 'SET_ADD') {
      let raw = String(val || '').trim();
      if (raw.startsWith('[') && raw.endsWith(']')) {
        raw = raw.substring(1, raw.length - 1);
      }
      const items = raw.split(',').map(s => s.trim()).filter(Boolean);
      if (items.length === 0) return '<span style="color:#94a3b8; font-style:italic;">[empty set]</span>';
      return `<div class="chip-container">${items.map(it => `<span class="chip-item">👤 ${it}</span>`).join('')}</div>`;
    }

    if (val === 'NEEDS_RESCUE') {
      return `<span class="badge" style="background:#fee2e2; color:#991b1b; font-weight:700;">🚨 NEEDS_RESCUE</span>`;
    }
    if (val === 'RESCUED') {
      return `<span class="badge" style="background:#ecfdf5; color:#065f46; font-weight:700;">✅ RESCUED</span>`;
    }
    return `<strong>${val}</strong>`;
  }

  // ── Load Updates ──
  async function loadUpdates() {
    try {
      currentUpdates = await api.get('/api/updates', {
        mockUrl: '/mock/reconcile.json',
        transformMock: (data) => {
          const mock = [];
          if (data && data.resolutions) {
            data.resolutions.forEach(res => {
              (res.contributingUpdates || []).forEach(cu => {
                mock.push({
                  recordId: cu.recordId,
                  resourceKey: res.resourceKey,
                  resourceType: res.resourceType,
                  value: cu.value,
                  clusterId: 'Cluster-A',
                  nodeId: cu.nodeId,
                  description: 'Mock update',
                  vector: cu.vector || {}
                });
              });
            });
          }
          return mock;
        }
      });

      renderUpdatesTable();
      renderTimeline();
      updateKpis();
      populateComparatorOptions();
    } catch (e) {
      console.warn('Failed to load updates:', e);
    }
  }

  // ── Render Updates Table ──
  function renderUpdatesTable() {
    const tbody = document.getElementById('updates-table-body');
    if (!tbody) return;

    if (!currentUpdates || currentUpdates.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="5" style="text-align: center; padding: 24px; color: #94a3b8; font-style: italic;">
            No updates recorded. Click "Load Partition Scenario" or record one below.
          </td>
        </tr>`;
      return;
    }

    tbody.innerHTML = currentUpdates.map(u => `
      <tr style="border-bottom: 1px solid #f1f5f9; font-size: 0.85rem;">
        <td style="padding: 8px 12px; font-weight: 600;">${u.recordId}</td>
        <td style="padding: 8px 12px;"><span class="badge badge-info">${u.resourceKey}</span></td>
        <td style="padding: 8px 12px;">${renderValueChips(u.resourceType, u.value)}</td>
        <td style="padding: 8px 12px;"><small><strong>${u.clusterId}</strong> &middot; ${u.nodeId}</small></td>
        <td style="padding: 8px 12px;">${formatVectorClock(u.vector)}</td>
      </tr>
    `).join('');
  }

  // ── Render Causal Timeline ──
  function renderTimeline() {
    const container = document.getElementById('causal-timeline');
    if (!container) return;

    if (!currentUpdates || currentUpdates.length === 0) {
      container.innerHTML = '<div style="color: #94a3b8; font-style: italic; font-size: 0.85rem;">Timeline empty.</div>';
      return;
    }

    container.innerHTML = currentUpdates.map(u => `
      <div class="timeline-item">
        <div class="timeline-dot ${u.resourceKey === 'House-12' ? 'concurrent' : ''}"></div>
        <div class="timeline-content">
          <div style="display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 4px;">
            <span style="font-weight: 700; color: #1e293b;">${u.recordId} &mdash; ${u.resourceKey}</span>
            <small style="color: #64748b;">${u.clusterId} (${u.nodeId})</small>
          </div>
          <div style="margin-bottom: 6px;">${renderValueChips(u.resourceType, u.value)}</div>
          <div style="font-size: 0.76rem; color: #64748b; margin-bottom: 4px;">${u.description || ''}</div>
          <div>${formatVectorClock(u.vector)}</div>
        </div>
      </div>
    `).join('');
  }

  // ── Populate Comparator Options ──
  function populateComparatorOptions() {
    const s1 = document.getElementById('cmp-update-1');
    const s2 = document.getElementById('cmp-update-2');
    if (!s1 || !s2) return;

    const optionsHtml = currentUpdates.map((u, idx) => `
      <option value="${u.recordId}">[${u.recordId}] ${u.resourceKey} = ${u.value} (${u.nodeId})</option>
    `).join('');

    s1.innerHTML = optionsHtml;
    s2.innerHTML = optionsHtml;

    if (currentUpdates.length >= 2) {
      s1.selectedIndex = 0;
      s2.selectedIndex = 1;
    }
  }

  // ── Compare Causality ──
  async function compareUpdates() {
    const s1 = document.getElementById('cmp-update-1');
    const s2 = document.getElementById('cmp-update-2');
    const resultBox = document.getElementById('compare-result');
    const summary = document.getElementById('cmp-summary');
    const details = document.getElementById('cmp-details');
    const badge = document.getElementById('cmp-badge');

    if (!s1 || !s2 || !s1.value || !s2.value) {
      if (window.toast) window.toast.warning('Please select two updates to compare.');
      return;
    }

    try {
      const res = await api.get(`/api/updates/compare?id1=${encodeURIComponent(s1.value)}&id2=${encodeURIComponent(s2.value)}`);
      resultBox.style.display = 'flex';

      badge.className = `relation-badge relation-${res.relation}`;
      badge.textContent = res.relation;

      if (res.relation === 'CONCURRENT') {
        summary.textContent = `⚡ Concurrent Updates (A || B) — Network Partition Detected!`;
        details.textContent = `Neither update happened before the other. A conflict exists and requires CRDT or safety-first policy resolution.`;
      } else if (res.relation === 'BEFORE') {
        summary.textContent = `➡️ Causal Precedence (${res.id1} → ${res.id2})`;
        details.textContent = `${res.id1} causally precedes ${res.id2}.`;
      } else if (res.relation === 'AFTER') {
        summary.textContent = `⬅️ Causal Precedence (${res.id2} → ${res.id1})`;
        details.textContent = `${res.id2} causally precedes ${res.id1}.`;
      } else {
        summary.textContent = `Identical Vector Clocks`;
        details.textContent = `Both updates occurred at the exact same logical vector state.`;
      }
    } catch (e) {
      console.warn('Comparison error:', e);
      if (window.toast) window.toast.error('Failed to compare updates.');
    }
  }

  // ── Submit New Update ──
  async function submitUpdate(e) {
    if (e) e.preventDefault();

    const resourceKey = document.getElementById('input-resource-key').value.trim();
    const resourceType = document.getElementById('select-resource-type').value;
    const value = document.getElementById('input-value').value.trim();
    const clusterId = document.getElementById('select-cluster').value;
    const nodeId = document.getElementById('select-node').value;
    const description = document.getElementById('input-description').value.trim();

    if (!resourceKey || !value) {
      if (window.toast) window.toast.warning('Resource Key and Value are required.');
      return;
    }

    try {
      const rec = await api.post('/api/updates', {
        resourceKey, resourceType, value, clusterId, nodeId, description
      });

      if (window.toast) window.toast.success(`Update ${rec.recordId} stamped & saved!`);
      await loadUpdates();
    } catch (err) {
      console.error('Failed to submit update:', err);
      if (window.toast) window.toast.error('Failed to record update.');
    }
  }

  // ── Reconcile ──
  async function runReconciliation() {
    try {
      const result = await api.post('/api/reconcile', {}, {
        mockUrl: '/mock/reconcile.json'
      });
      latestReconciliation = result;
      renderResolutions(result);
      renderTrace(result.steps);
      updateKpis();

      if (window.toast) {
        if (result.conflictsDetected > 0) {
          window.toast.warning(`Reconciled with ${result.conflictsDetected} conflict(s) requiring human review.`);
        } else {
          window.toast.success(`Reconciliation complete! All resources resolved.`);
        }
      }
    } catch (err) {
      console.error('Reconciliation failed:', err);
      if (window.toast) window.toast.error('Reconciliation failed.');
    }
  }

  // ── Render Resolutions ──
  function renderResolutions(result) {
    const tbody = document.getElementById('resolutions-table-body');
    if (!tbody) return;

    if (!result || !result.resolutions || result.resolutions.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="6" style="text-align: center; padding: 24px; color: #94a3b8; font-style: italic;">
            No resolutions generated.
          </td>
        </tr>`;
      return;
    }

    tbody.innerHTML = result.resolutions.map(r => `
      <tr style="border-bottom: 1px solid #f1f5f9; font-size: 0.85rem;">
        <td style="padding: 10px 14px; font-weight: 700;">${r.resourceKey}</td>
        <td style="padding: 10px 14px;"><span class="badge badge-info">${r.resourceType}</span></td>
        <td style="padding: 10px 14px;">${renderValueChips(r.resourceType, r.finalValue)}</td>
        <td style="padding: 10px 14px;"><span class="badge" style="background:#f1f5f9; color:#475569;">${r.method}</span></td>
        <td style="padding: 10px 14px;">
          ${r.needsHumanReview
            ? '<span class="badge-review">🚨 NEEDS REVIEW</span>'
            : '<span class="badge-auto">✅ AUTO-RESOLVED</span>'}
        </td>
        <td style="padding: 10px 14px; color: #475569;">${r.explanation}</td>
      </tr>
    `).join('');
  }

  // ── Render Trace ──
  function renderTrace(steps) {
    const box = document.getElementById('engine-trace');
    if (!box) return;

    if (!steps || steps.length === 0) {
      box.innerHTML = '<div style="color: #64748b; font-style: italic;">No execution trace.</div>';
      return;
    }

    box.innerHTML = steps.map(s => {
      let color = '#93c5fd';
      if (s.includes('conflict') || s.includes('NEEDS_RESCUE')) color = '#fca5a5';
      if (s.includes('complete') || s.includes('unioned')) color = '#86efac';
      return `<div style="margin-bottom: 4px; color: ${color};">&gt; ${s}</div>`;
    }).join('');
  }

  // ── Update KPIs ──
  function updateKpis() {
    const totalEl = document.getElementById('kpi-total-updates');
    const distinctEl = document.getElementById('kpi-distinct-resources');
    const conflictsEl = document.getElementById('kpi-conflicts');
    const conflictsSubEl = document.getElementById('kpi-conflicts-sub');
    const reviewEl = document.getElementById('kpi-review');

    if (totalEl) totalEl.textContent = currentUpdates.length;

    const distinct = new Set(currentUpdates.map(u => u.resourceKey));
    if (distinctEl) distinctEl.textContent = distinct.size;

    if (latestReconciliation) {
      if (conflictsEl) conflictsEl.textContent = latestReconciliation.conflictsDetected;
      if (conflictsSubEl) {
        conflictsSubEl.textContent = latestReconciliation.conflictsDetected > 0 ? 'Safety policy applied' : 'No conflicts';
      }
      if (reviewEl) reviewEl.textContent = latestReconciliation.humanReviewRequired;
    }
  }

  // ── Seed Scenario ──
  async function seedScenario() {
    try {
      await api.post('/api/updates/seed', {});
      if (window.toast) window.toast.success('Partition scenario seeded! Running reconciliation...');
      await loadUpdates();
      await runReconciliation();
    } catch (e) {
      console.error('Seed error:', e);
      if (window.toast) window.toast.error('Failed to seed scenario.');
    }
  }

  // ── Reset All ──
  async function resetAll() {
    try {
      await api.delete('/api/updates');
      latestReconciliation = null;
      document.getElementById('resolutions-table-body').innerHTML = `
        <tr><td colspan="6" style="text-align: center; padding: 24px; color: #94a3b8; font-style: italic;">All updates cleared.</td></tr>`;
      document.getElementById('engine-trace').innerHTML = `
        <div style="color: #64748b; font-style: italic;">Engine trace cleared.</div>`;
      if (window.toast) window.toast.info('All updates and vector clocks reset.');
      await loadUpdates();
    } catch (e) {
      console.error('Reset error:', e);
      if (window.toast) window.toast.error('Failed to reset updates.');
    }
  }

  // ── Document Ready ──
  document.addEventListener('DOMContentLoaded', () => {
    // Buttons
    document.getElementById('form-add-update')?.addEventListener('submit', submitUpdate);
    document.getElementById('btn-reconcile')?.addEventListener('click', runReconciliation);
    document.getElementById('btn-reconcile-inner')?.addEventListener('click', runReconciliation);
    document.getElementById('btn-seed')?.addEventListener('click', seedScenario);
    document.getElementById('btn-reset-updates')?.addEventListener('click', resetAll);
    document.getElementById('btn-compare')?.addEventListener('click', compareUpdates);

    // Dynamic field update when resource type changes
    const typeSelect = document.getElementById('select-resource-type');
    const valInput = document.getElementById('input-value');
    if (typeSelect && valInput) {
      typeSelect.addEventListener('change', () => {
        if (typeSelect.value === 'SET_ADD') {
          valInput.placeholder = 'e.g. John Doe (Survivor Name)';
          valInput.value = 'Survivor Alpha';
        } else {
          valInput.placeholder = 'e.g. NEEDS_RESCUE or RESCUED';
          valInput.value = 'NEEDS_RESCUE';
        }
      });
    }

    // Live SSE
    if (window.events) {
      events.createLiveLog('reconcile-live-log', { maxEntries: 50 });
      events.on('consensus', 'UPDATE_RECORDED', () => loadUpdates());
      events.on('consensus', 'RECONCILED', () => loadUpdates());
      events.on('consensus', 'UPDATES_CLEARED', () => loadUpdates());
    }

    // Initial load
    loadUpdates();
  });

})();

