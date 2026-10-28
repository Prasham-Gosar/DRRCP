/**
 * election.js — Controller script for Leader Election (Exp 4)
 * Author: Grasit (Consensus & Resilience)
 *
 * Provides:
 * - Node cards rendering with priority and alive/dead toggles
 * - Dynamic initiator selection dropdown
 * - Execution of Bully, Ring, and Compare algorithms via REST API
 * - Animated step-by-step message trace between cards
 * - Message count comparison bar chart using Chart.js
 * - Real-time election history table updates
 * - Live SSE events integration for consensus events
 */

document.addEventListener('DOMContentLoaded', () => {
  'use strict';

  // ── Initial State ──
  let nodes = [
    { id: 'UAV-01',  priority: 1, alive: true },
    { id: 'UAV-02',  priority: 2, alive: true },
    { id: 'Edge-01', priority: 3, alive: true },
    { id: 'Edge-02', priority: 4, alive: true },
    { id: 'Base-01', priority: 5, alive: true }
  ];
  let runCount = 0;
  let lastBully = null;
  let lastRing = null;
  let currentLeaderId = null;
  let animating = false;

  // ── Chart Setup ──
  const canvas = document.getElementById('msg-chart');
  let msgChart = null;
  if (canvas) {
    const ctx = canvas.getContext('2d');
    msgChart = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: [],
        datasets: [
          { label: 'Bully Messages', data: [], backgroundColor: 'rgba(37,99,235,0.7)', borderRadius: 4 },
          { label: 'Ring Messages',  data: [], backgroundColor: 'rgba(124,58,237,0.7)', borderRadius: 4 }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'top', labels: { font: { size: 11 } } }
        },
        scales: {
          y: { beginAtZero: true, title: { display: true, text: 'Messages' } },
          x: { title: { display: true, text: 'Election Run' } }
        }
      }
    });
  }

  // ── Render Node Grid ──
  function renderNodeGrid() {
    const grid = document.getElementById('node-grid');
    const select = document.getElementById('initiator-select');
    if (!grid || !select) return;

    const currentVal = select.value || (nodes.find(n => n.alive) ? nodes.find(n => n.alive).id : nodes[0].id);

    grid.innerHTML = nodes.map((n, i) => {
      const statusClass = n.alive ? 'alive' : 'dead';
      const leaderClass = n.id === currentLeaderId ? ' leader' : '';
      const initClass = n.id === currentVal ? ' initiator' : '';
      return `
        <div class="node-card ${statusClass}${leaderClass}${initClass}" id="card-${n.id}" data-id="${n.id}">
          <div class="node-id">${n.id}</div>
          <div class="node-priority">Priority: ${n.priority}</div>
          <label class="toggle-alive">
            <input type="checkbox" ${n.alive ? 'checked' : ''} data-idx="${i}">
            <span>${n.alive ? 'ALIVE' : 'DEAD'}</span>
          </label>
          ${n.id === currentLeaderId ? '<div class="node-role-badge leader-badge">👑 LEADER</div>' : ''}
          ${n.id === currentVal && n.alive ? '<div class="node-role-badge initiator-badge">📢 INITIATOR</div>' : ''}
        </div>
      `;
    }).join('');

    // Wire alive toggles
    grid.querySelectorAll('input[type="checkbox"]').forEach(cb => {
      cb.addEventListener('change', (e) => {
        const idx = parseInt(e.target.dataset.idx);
        nodes[idx].alive = e.target.checked;
        updateKpis();
        renderNodeGrid();
      });
    });

    // Populate Initiator dropdown
    select.innerHTML = nodes
      .filter(n => n.alive)
      .map(n => `<option value="${n.id}" ${n.id === currentVal ? 'selected' : ''}>${n.id} (p=${n.priority})</option>`)
      .join('');

    updateKpis();
  }

  function updateKpis() {
    const alive = nodes.filter(n => n.alive).length;
    const kpiNodes = document.getElementById('kpi-nodes');
    const kpiBadge = document.getElementById('kpi-alive-badge');
    if (kpiNodes) kpiNodes.textContent = nodes.length;
    if (kpiBadge) {
      kpiBadge.textContent = alive + ' ALIVE';
      kpiBadge.className = `badge ${alive === nodes.length ? 'badge-green' : 'badge-amber'}`;
    }
  }

  // ── Step Animation ──
  function getAnimSpeed() {
    const slider = document.getElementById('anim-speed');
    return slider ? parseInt(slider.value) : 300;
  }

  const speedSlider = document.getElementById('anim-speed');
  if (speedSlider) {
    speedSlider.addEventListener('input', (e) => {
      const label = document.getElementById('speed-label');
      if (label) label.textContent = e.target.value + 'ms';
    });
  }

  async function animateSteps(steps) {
    animating = true;
    const container = document.getElementById('trace-container');
    if (!container) return;
    container.innerHTML = '';

    for (let i = 0; i < steps.length; i++) {
      const step = steps[i];
      const div = document.createElement('div');
      div.className = 'step-line active';

      // Categorize line color
      if (step.includes('--ELECTION')) div.className += ' election-msg';
      else if (step.includes('--COORDINATOR') || step.includes('--COORDINATOR(leader')) div.className += ' coordinator-msg';
      else if (step.includes('--ALIVE') || step.includes('--OK')) div.className += ' alive-msg';
      else if (step.includes('Election complete') || step.includes('declared leader')) div.className += ' result-msg';

      div.textContent = step;
      container.appendChild(div);
      container.scrollTop = container.scrollHeight;

      // Pulse visual effect on node card mentioned in trace step
      highlightNodesInStep(step);

      if (i > 0) {
        container.children[i - 1].classList.remove('active');
      }

      await sleep(getAnimSpeed());
    }
    animating = false;
  }

  function highlightNodesInStep(step) {
    nodes.forEach(n => {
      const card = document.getElementById('card-' + n.id);
      if (card && step.includes(n.id)) {
        card.classList.add('highlight-msg');
        setTimeout(() => card.classList.remove('highlight-msg'), 500);
      }
    });
  }

  function sleep(ms) {
    return new Promise(r => setTimeout(r, ms));
  }

  // ── API Actions ──
  function buildRequest() {
    const select = document.getElementById('initiator-select');
    return {
      nodes: nodes.map(n => ({ id: n.id, priority: n.priority, alive: n.alive })),
      initiatorId: select ? select.value : nodes[0].id,
      clusterId: 'Cluster-A'
    };
  }

  function setButtonsDisabled(disabled) {
    ['btn-bully', 'btn-ring', 'btn-compare'].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.disabled = disabled;
    });
  }

  async function runElection(endpoint, label) {
    if (animating) return;

    const aliveCount = nodes.filter(n => n.alive).length;
    if (aliveCount === 0) {
      if (window.toast) toast.error('No Alive Nodes', 'At least one node must be alive to run an election.');
      return;
    }

    const select = document.getElementById('initiator-select');
    const initiator = select ? select.value : '';
    const initiatorNode = nodes.find(n => n.id === initiator);
    if (!initiatorNode || !initiatorNode.alive) {
      if (window.toast) toast.error('Invalid Initiator', 'The initiator node must be alive.');
      return;
    }

    setButtonsDisabled(true);

    try {
      const result = await api.post('/api/election/' + endpoint, buildRequest());

      if (endpoint === 'compare') {
        lastBully = result.bully;
        lastRing = result.ring;
        currentLeaderId = result.bully.leaderId;
        runCount++;

        await animateSteps([
          '═══════════════════════════════════════════',
          '  BULLY ELECTION TRACE',
          '═══════════════════════════════════════════',
          ...result.bully.steps,
          '',
          '═══════════════════════════════════════════',
          '  RING ELECTION TRACE',
          '═══════════════════════════════════════════',
          ...result.ring.steps,
          '',
          `▶ Leader elected: ${result.bully.leaderId} (Both agree: ${result.sameLeader ? 'YES ✓' : 'NO ✗'})`,
          `▶ Message overhead: Bully = ${result.bullyMessages} msgs | Ring = ${result.ringMessages} msgs`
        ]);

        addChartPoint('Run ' + runCount, result.bullyMessages, result.ringMessages);
        document.getElementById('kpi-messages').textContent = result.bullyMessages + result.ringMessages;
        document.getElementById('kpi-algo-badge').textContent = 'COMPARE';
        if (window.toast) toast.success('Comparison Complete', `Leader: ${result.bully.leaderId}. Same: ${result.sameLeader}`);

      } else {
        if (endpoint === 'bully') lastBully = result;
        else lastRing = result;
        currentLeaderId = result.leaderId;
        runCount++;

        await animateSteps(result.steps);

        const msgB = endpoint === 'bully' ? result.totalMessages : (lastBully ? lastBully.totalMessages : 0);
        const msgR = endpoint === 'ring' ? result.totalMessages : (lastRing ? lastRing.totalMessages : 0);
        addChartPoint('Run ' + runCount, msgB, msgR);

        document.getElementById('kpi-messages').textContent = result.totalMessages;
        document.getElementById('kpi-algo-badge').textContent = result.algorithm;
        if (window.toast) toast.success('Election Complete', `${result.algorithm}: Leader = ${result.leaderId} (${result.totalMessages} msgs)`);
      }

      const kpiLeader = document.getElementById('kpi-leader');
      const kpiRuns = document.getElementById('kpi-runs');
      if (kpiLeader) kpiLeader.textContent = currentLeaderId;
      if (kpiRuns) kpiRuns.textContent = runCount;

      renderNodeGrid();
      loadHistory();

    } catch (e) {
      if (window.toast) toast.error('Election Failed', e.message);
    } finally {
      setButtonsDisabled(false);
    }
  }

  function addChartPoint(label, bully, ring) {
    if (!msgChart) return;
    msgChart.data.labels.push(label);
    msgChart.data.datasets[0].data.push(bully);
    msgChart.data.datasets[1].data.push(ring);
    msgChart.update();
  }

  // ── History ──
  async function loadHistory() {
    try {
      const data = await api.get('/api/election/history?limit=10');
      const tbody = document.getElementById('history-table-body');
      if (!tbody) return;

      if (!Array.isArray(data) || data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; color:#94a3b8; padding:20px;">No elections run yet.</td></tr>';
        return;
      }

      tbody.innerHTML = data.map(r => {
        const time = r.timestamp ? new Date(r.timestamp).toLocaleTimeString() : 'N/A';
        const algoBadge = r.algorithm === 'BULLY'
          ? '<span class="badge badge-info">BULLY</span>'
          : '<span class="badge badge-purple">RING</span>';
        return `
          <tr>
            <td>${r.id || '-'}</td>
            <td>${algoBadge}</td>
            <td><strong>${r.leaderId}</strong></td>
            <td>${r.leaderPriority}</td>
            <td>${r.totalMessages}</td>
            <td>${r.clusterId || 'Cluster-A'}</td>
            <td class="font-mono">${time}</td>
          </tr>
        `;
      }).join('');
    } catch (e) {
      console.warn('Could not load election history:', e);
    }
  }

  // ── Reset ──
  function resetPage() {
    nodes = [
      { id: 'UAV-01',  priority: 1, alive: true },
      { id: 'UAV-02',  priority: 2, alive: true },
      { id: 'Edge-01', priority: 3, alive: true },
      { id: 'Edge-02', priority: 4, alive: true },
      { id: 'Base-01', priority: 5, alive: true }
    ];
    currentLeaderId = null;
    lastBully = null;
    lastRing = null;
    runCount = 0;

    const kpiLeader = document.getElementById('kpi-leader');
    const kpiMessages = document.getElementById('kpi-messages');
    const kpiRuns = document.getElementById('kpi-runs');
    const kpiAlgo = document.getElementById('kpi-algo-badge');
    const traceEl = document.getElementById('trace-container');

    if (kpiLeader) kpiLeader.textContent = '—';
    if (kpiMessages) kpiMessages.textContent = '0';
    if (kpiRuns) kpiRuns.textContent = '0';
    if (kpiAlgo) kpiAlgo.textContent = 'NONE';
    if (traceEl) {
      traceEl.innerHTML = '<div style="color: #64748b; font-style: italic; padding: 24px; text-align: center;">Configure nodes above and click an election button to see the step-by-step trace here.</div>';
    }

    if (msgChart) {
      msgChart.data.labels = [];
      msgChart.data.datasets[0].data = [];
      msgChart.data.datasets[1].data = [];
      msgChart.update();
    }

    renderNodeGrid();
    if (window.toast) toast.info('Reset', 'Election page reset to default state.');
  }

  // ── Event Handlers ──
  const btnBully = document.getElementById('btn-bully');
  const btnRing = document.getElementById('btn-ring');
  const btnCompare = document.getElementById('btn-compare');
  const btnReset = document.getElementById('btn-reset');
  const btnClearTrace = document.getElementById('btn-clear-trace');
  const initiatorSelect = document.getElementById('initiator-select');

  if (btnBully) btnBully.addEventListener('click', () => runElection('bully', 'bully'));
  if (btnRing) btnRing.addEventListener('click', () => runElection('ring', 'ring'));
  if (btnCompare) btnCompare.addEventListener('click', () => runElection('compare', 'compare'));
  if (btnReset) btnReset.addEventListener('click', resetPage);
  if (btnClearTrace) {
    btnClearTrace.addEventListener('click', () => {
      const traceEl = document.getElementById('trace-container');
      if (traceEl) traceEl.innerHTML = '<div style="color: #64748b; font-style: italic; padding: 24px; text-align: center;">Trace cleared.</div>';
    });
  }
  if (initiatorSelect) initiatorSelect.addEventListener('change', renderNodeGrid);

  // ── SSE Live Stream ──
  if (window.events) {
    events.createLiveLog('module-live-log', { maxEntries: 50 });
    events.on('consensus', 'LEADER_ELECTED', (payload) => {
      if (payload && payload.leaderId) {
        const kpiLeader = document.getElementById('kpi-leader');
        if (kpiLeader) kpiLeader.textContent = payload.leaderId;
      }
    });
  }

  // ── Initialization ──
  renderNodeGrid();
  loadHistory();
});

