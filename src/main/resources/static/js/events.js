/**
 * events.js - Server-Sent Events (SSE) Client & Reusable Live-Log Widget
 * Author: Vedant (Frontend Shell)
 *
 * Usage:
 *   events.on('comm', 'COMM_BCAST', (data) => console.log('Bcast received', data));
 *   events.onAll((evt) => console.log('Any event', evt));
 *   events.createLiveLog('live-log-container', { maxEntries: 100 });
 */

(function () {
  'use strict';

  class EventBus {
    constructor() {
      this.listeners = new Map(); // key: "module:type" -> Set of callbacks
      this.moduleListeners = new Map(); // key: "module" -> Set of callbacks
      this.allListeners = new Set();
      this.eventSource = null;
      this.reconnectAttempts = 0;
      this.maxReconnectDelay = 15000;
      this.isConnected = false;
      this.mockTimer = null;

      this.init();
    }

    init() {
      const isMock = (window.api && window.api.isMock()) ||
                     new URLSearchParams(window.location.search).get('mock') === '1' ||
                     localStorage.getItem('drs_mock_mode') === 'true';

      if (isMock) {
        this.startMockEventStream();
      } else {
        this.connectSse();
      }
    }

    connectSse() {
      if (this.eventSource) {
        this.eventSource.close();
      }

      this.updateStatus(false, 'Connecting...');

      try {
        this.eventSource = new EventSource('/api/events');

        this.eventSource.onopen = () => {
          this.reconnectAttempts = 0;
          this.isConnected = true;
          this.updateStatus(true, 'Live Events Stream');
        };

        this.eventSource.onmessage = (e) => {
          this.handleRawMessage(e.data);
        };

        this.eventSource.onerror = () => {
          this.isConnected = false;
          this.eventSource.close();
          this.updateStatus(false, 'Disconnected (Reconnecting...)');
          this.scheduleReconnect();
        };
      } catch (err) {
        console.error('[Events] SSE initialization failed:', err);
        this.scheduleReconnect();
      }
    }

    scheduleReconnect() {
      this.reconnectAttempts++;
      const delay = Math.min(1000 * Math.pow(1.5, this.reconnectAttempts), this.maxReconnectDelay);
      setTimeout(() => this.connectSse(), delay);
    }

    handleRawMessage(dataString) {
      if (!dataString) return;
      try {
        const raw = JSON.parse(dataString);
        let parsedPayload = raw.payload;

        // In DRS, EventPublisher serializes payload as a JSON string inside raw.payload
        if (typeof raw.payload === 'string') {
          try {
            parsedPayload = JSON.parse(raw.payload);
          } catch (_) {
            parsedPayload = raw.payload;
          }
        }

        const evt = {
          id: raw.id || Date.now(),
          module: (raw.module || 'system').toLowerCase(),
          type: raw.type || 'UNKNOWN',
          payload: parsedPayload,
          timestamp: raw.timestamp || new Date().toISOString()
        };

        this.dispatch(evt);
      } catch (err) {
        console.warn('[Events] Failed to parse SSE event message:', err, dataString);
      }
    }

    dispatch(evt) {
      // 1. Exact match "module:type"
      const key = `${evt.module}:${evt.type}`;
      if (this.listeners.has(key)) {
        for (const cb of this.listeners.get(key)) {
          try { cb(evt.payload, evt); } catch (e) { console.error(e); }
        }
      }

      // 2. Module wildcard match
      if (this.moduleListeners.has(evt.module)) {
        for (const cb of this.moduleListeners.get(evt.module)) {
          try { cb(evt.payload, evt); } catch (e) { console.error(e); }
        }
      }

      // 3. Global listener
      for (const cb of this.allListeners) {
        try { cb(evt); } catch (e) { console.error(e); }
      }
    }

    on(module, type, callback) {
      const key = `${module.toLowerCase()}:${type}`;
      if (!this.listeners.has(key)) {
        this.listeners.set(key, new Set());
      }
      this.listeners.get(key).add(callback);
      return () => this.off(module, type, callback);
    }

    onModule(module, callback) {
      const m = module.toLowerCase();
      if (!this.moduleListeners.has(m)) {
        this.moduleListeners.set(m, new Set());
      }
      this.moduleListeners.get(m).add(callback);
      return () => {
        if (this.moduleListeners.has(m)) {
          this.moduleListeners.get(m).delete(callback);
        }
      };
    }

    onAll(callback) {
      this.allListeners.add(callback);
      return () => this.allListeners.delete(callback);
    }

    off(module, type, callback) {
      const key = `${module.toLowerCase()}:${type}`;
      if (this.listeners.has(key)) {
        this.listeners.get(key).delete(callback);
      }
    }

    updateStatus(connected, text) {
      const pill = document.getElementById('sse-indicator');
      const textEl = document.getElementById('sse-status-text');
      if (pill) {
        if (connected) {
          pill.classList.remove('disconnected');
        } else {
          pill.classList.add('disconnected');
        }
      }
      if (textEl) {
        textEl.textContent = text;
      }
    }

    // ── Mock Stream for ?mock=1 ──
    startMockEventStream() {
      this.isConnected = true;
      this.updateStatus(true, 'Mock Event Stream (Simulated)');

      const mockEventsPool = [
        { module: 'core', type: 'HEARTBEAT', payload: { nodeId: 'UAV-01', battery: 84, signal: 92, status: 'ALIVE' } },
        { module: 'comm', type: 'COMM_BCAST', payload: { operation: 'BCAST', ranks: 4, alert: [911, 12, 5], status: 'SUCCESS' } },
        { module: 'consensus', type: 'LEADER_ELECTED', payload: { algorithm: 'BULLY', leaderId: 'Edge-01', totalMessages: 8 } },
        { module: 'compute', type: 'TASK_ASSIGNED', payload: { taskId: 'TASK-104', assignedTo: 'Edge-02', priority: 'HIGH' } },
        { module: 'core', type: 'CLOCK_SYNCED', payload: { nodeId: 'UAV-02', offsetMs: 14, rttMs: 8 } },
        { module: 'ft', type: 'FT_REPLICATED', payload: { resourceId: 'House-12', primary: 'Primary-01', backup: 'Backup-01' } },
        { module: 'comm', type: 'COMM_SCATTER', payload: { chunkSize: 2, ranks: 4, packages: [10, 20, 30, 40, 50, 60, 70, 80] } }
      ];

      let idx = 0;
      this.mockTimer = setInterval(() => {
        const item = mockEventsPool[idx % mockEventsPool.length];
        idx++;
        this.dispatch({
          id: Date.now(),
          module: item.module,
          type: item.type,
          payload: item.payload,
          timestamp: new Date().toISOString()
        });
      }, 4500);
    }

    // ── Reusable Live-Log UI Widget Component ──
    createLiveLog(containerId, options = {}) {
      const container = typeof containerId === 'string' ? document.getElementById(containerId) : containerId;
      if (!container) return null;

      const maxEntries = options.maxEntries || 100;
      const initialFilter = options.filterModule || 'all';

      container.innerHTML = `
        <div class="live-log-widget">
          <div class="log-toolbar">
            <div style="display:flex; align-items:center; gap:8px;">
              <span class="sse-status-pill" style="font-size:0.7rem; padding:2px 8px;">
                <span class="sse-pulse" style="width:6px; height:6px;"></span>
                <span>STREAM</span>
              </span>
              <strong style="font-size:0.85rem;">Live Event Stream</strong>
              <span id="${container.id}-count" class="badge badge-gray" style="font-size:0.7rem;">0 events</span>
            </div>
            <div style="display:flex; align-items:center; gap:8px;">
              <select id="${container.id}-filter" class="form-select" style="width:auto; padding:3px 8px; font-size:0.78rem;">
                <option value="all">All Modules</option>
                <option value="core">core (Exp 1-3)</option>
                <option value="consensus">consensus (Exp 4-5)</option>
                <option value="comm">comm (Exp 9-10)</option>
                <option value="compute">compute (Exp 6-7)</option>
                <option value="ft">fault tolerance (Exp 8)</option>
              </select>
              <input type="text" id="${container.id}-search" placeholder="Filter type..." 
                     class="form-input" style="width:110px; padding:3px 8px; font-size:0.78rem;" />
              <button id="${container.id}-pause-btn" class="btn btn-outline btn-sm" title="Pause live scrolling">⏸️</button>
              <button id="${container.id}-clear-btn" class="btn btn-outline btn-sm" title="Clear log">🗑️</button>
            </div>
          </div>
          <div class="log-stream" id="${container.id}-stream">
            <div id="${container.id}-empty" style="text-align:center; padding:32px; color:#94a3b8; font-size:0.82rem;">
              Listening for events on /api/events...
            </div>
          </div>
        </div>
      `;

      const streamEl = document.getElementById(`${container.id}-stream`);
      const countEl = document.getElementById(`${container.id}-count`);
      const filterSelect = document.getElementById(`${container.id}-filter`);
      const searchInput = document.getElementById(`${container.id}-search`);
      const pauseBtn = document.getElementById(`${container.id}-pause-btn`);
      const clearBtn = document.getElementById(`${container.id}-clear-btn`);
      const emptyEl = document.getElementById(`${container.id}-empty`);

      let entries = [];
      let isPaused = false;

      function renderEntries() {
        const filterModule = filterSelect.value.toLowerCase();
        const searchTerm = searchInput.value.trim().toLowerCase();

        const filtered = entries.filter(e => {
          if (filterModule !== 'all' && e.module !== filterModule) return false;
          if (searchTerm && !e.type.toLowerCase().includes(searchTerm) && 
              !JSON.stringify(e.payload).toLowerCase().includes(searchTerm)) {
            return false;
          }
          return true;
        });

        countEl.textContent = `${filtered.length} event${filtered.length === 1 ? '' : 's'}`;

        if (filtered.length === 0) {
          streamEl.innerHTML = `<div style="text-align:center; padding:32px; color:#94a3b8; font-size:0.82rem;">No matching events.</div>`;
          return;
        }

        streamEl.innerHTML = filtered.map(evt => {
          const timeStr = new Date(evt.timestamp).toLocaleTimeString();
          const badgeClass = getModuleBadgeClass(evt.module);
          const payloadStr = typeof evt.payload === 'object' ? JSON.stringify(evt.payload) : String(evt.payload);

          return `
            <div class="log-entry" title="Click to inspect payload" onclick="console.log(${escapeForAttr(JSON.stringify(evt))})">
              <span class="log-time">${timeStr}</span>
              <span class="badge ${badgeClass}">${evt.module}</span>
              <span class="log-type">${evt.type}</span>
              <span class="log-payload-preview font-mono">${escapeHtml(payloadStr)}</span>
            </div>
          `;
        }).join('');

        if (!isPaused) {
          streamEl.scrollTop = streamEl.scrollHeight;
        }
      }

      function getModuleBadgeClass(mod) {
        switch (mod) {
          case 'core': return 'badge-info';
          case 'comm': return 'badge-purple';
          case 'consensus': return 'badge-amber';
          case 'compute': return 'badge-green';
          case 'ft': return 'badge-danger';
          default: return 'badge-gray';
        }
      }

      function escapeHtml(str) {
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
      }

      function escapeForAttr(str) {
        return str.replace(/'/g, "\\'").replace(/"/g, '&quot;');
      }

      function addEvent(evt) {
        if (emptyEl && emptyEl.parentNode) {
          emptyEl.parentNode.removeChild(emptyEl);
        }
        entries.push(evt);
        if (entries.length > maxEntries) {
          entries.shift();
        }
        renderEntries();
      }

      // Attach control event listeners
      filterSelect.addEventListener('change', renderEntries);
      searchInput.addEventListener('input', renderEntries);
      clearBtn.addEventListener('click', () => {
        entries = [];
        renderEntries();
      });
      pauseBtn.addEventListener('click', () => {
        isPaused = !isPaused;
        pauseBtn.textContent = isPaused ? '▶️' : '⏸️';
        pauseBtn.title = isPaused ? 'Resume auto-scroll' : 'Pause auto-scroll';
      });

      // Subscribe to all events
      this.onAll(addEvent);

      // Pre-fetch initial event history from /api/events/history
      if (window.api) {
        window.api.get('/api/events/history?limit=30')
          .then(history => {
            if (Array.isArray(history) && history.length > 0) {
              // History arrives newest first or oldest first; sort chronologically
              history.forEach(item => {
                let parsed = item.payload;
                if (typeof item.payload === 'string') {
                  try { parsed = JSON.parse(item.payload); } catch (_) {}
                }
                entries.push({
                  id: item.id,
                  module: (item.module || 'system').toLowerCase(),
                  type: item.type,
                  payload: parsed,
                  timestamp: item.timestamp
                });
              });
              renderEntries();
            }
          })
          .catch(() => {
            // Silently ignore if history endpoint is not yet populated
          });
      }

      return {
        addEvent,
        clear: () => { entries = []; renderEntries(); }
      };
    }
  }

  window.events = new EventBus();
})();
