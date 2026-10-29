/**
 * layout.js - Injects the sidebar nav and top header across all DRS Web pages
 * Author: Vedant (Frontend Shell)
 */

(function () {
  'use strict';

  // Navigation Items Specification (Matches ARCHITECTURE.md)
  const NAV_ITEMS = [
    { section: 'Overview' },
    { id: 'dashboard', label: 'Dashboard', icon: '📊', path: '/index.html', aliases: ['/', '/index.html'] },
    
    { section: 'Core & Infrastructure' },
    { id: 'nodes', label: 'Node Coordinator', icon: '📡', path: '/pages/nodes.html', badge: 'Exp 1-2' },
    { id: 'clock', label: 'Clock Synchronization', icon: '⏱️', path: '/pages/clock.html', badge: 'Exp 3' },
    
    { section: 'Consensus & Resilience' },
    { id: 'election', label: 'Leader Election', icon: '🗳️', path: '/pages/election.html', badge: 'Exp 4' },
    { id: 'reconcile', label: 'Reconciliation & CRDT', icon: '🔄', path: '/pages/reconcile.html', badge: 'Exp 5' },
    { id: 'faulttolerance', label: 'Fault Tolerance', icon: '🛡️', path: '/pages/faulttolerance.html', badge: 'Exp 8' },
    
    { section: 'Compute & Communication' },
    { id: 'loadbalancer', label: 'Load Balancer', icon: '⚖️', path: '/pages/loadbalancer.html', badge: 'Exp 6' },
    { id: 'analytics', label: 'Analytics & Spark', icon: '⚡', path: '/pages/analytics.html', badge: 'Exp 7' },
    { id: 'comm', label: 'Collective Comm', icon: '📢', path: '/pages/comm.html', aliases: ['/comm.html', '/pages/comm.html'], badge: 'Exp 9' },
    { id: 'matrix', label: 'Parallel Matrix', icon: '🧮', path: '/pages/matrix.html', aliases: ['/matrix.html', '/pages/matrix.html'], badge: 'Exp 10' },

    { section: 'System Verification' },
    { id: 'scenario', label: 'Scenario Runner', icon: '🚀', path: '/pages/scenario.html', badge: 'Full Story' },
    { id: 'about', label: 'About & Experiments', icon: '📖', path: '/about.html', aliases: ['/about.html', '/pages/about.html'], badge: '10 Exps' }
  ];

  // Helper to determine active route
  function getActiveNavId() {
    const currentPath = window.location.pathname.toLowerCase();
    for (const item of NAV_ITEMS) {
      if (item.aliases && item.aliases.some(alias => currentPath.endsWith(alias) || currentPath === alias)) {
        return item.id;
      }
      if (item.path && (currentPath.endsWith(item.path.toLowerCase()) || currentPath === item.path.toLowerCase())) {
        return item.id;
      }
    }
    // Default fallback
    if (currentPath === '' || currentPath === '/' || currentPath.endsWith('index.html')) {
      return 'dashboard';
    }
    return '';
  }

  // Toast Notification System
  function initToastContainer() {
    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      document.body.appendChild(container);
    }

    window.toast = {
      show(type, title, message, duration = 4000) {
        const icons = {
          success: '✅',
          error: '❌',
          warning: '⚠️',
          info: 'ℹ️'
        };

        const toastEl = document.createElement('div');
        toastEl.className = `toast toast-${type}`;
        toastEl.innerHTML = `
          <div class="toast-icon">${icons[type] || 'ℹ️'}</div>
          <div class="toast-content">
            <div class="toast-title">${escapeHtml(title)}</div>
            ${message ? `<div class="toast-message">${escapeHtml(message)}</div>` : ''}
          </div>
          <button class="toast-close" title="Close">&times;</button>
        `;

        toastEl.querySelector('.toast-close').addEventListener('click', () => {
          dismissToast(toastEl);
        });

        container.appendChild(toastEl);

        if (duration > 0) {
          setTimeout(() => dismissToast(toastEl), duration);
        }
      },
      success(title, message) { this.show('success', title, message); },
      error(title, message) { this.show('error', title, message, 6000); },
      warning(title, message) { this.show('warning', title, message); },
      info(title, message) { this.show('info', title, message); }
    };
  }

  function dismissToast(el) {
    if (!el || el.dataset.dismissing) return;
    el.dataset.dismissing = 'true';
    el.classList.add('toast-fadeout');
    setTimeout(() => {
      if (el.parentNode) el.parentNode.removeChild(el);
    }, 250);
  }

  function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  // Render Sidebar and Header Shell
  function injectShell() {
    const activeId = getActiveNavId();
    const activeItem = NAV_ITEMS.find(item => item.id === activeId) || { label: 'Platform' };

    // 1. Sidebar HTML
    let navHtml = '';
    for (const item of NAV_ITEMS) {
      if (item.section) {
        navHtml += `<div class="nav-section-title">${item.section}</div>`;
      } else {
        const isActive = item.id === activeId ? 'active' : '';
        const badgeHtml = item.badge ? `<span class="nav-badge">${item.badge}</span>` : '';
        navHtml += `
          <a href="${item.path}" class="nav-item ${isActive}" data-id="${item.id}">
            <span>${item.icon}</span>
            <span>${item.label}</span>
            ${badgeHtml}
          </a>
        `;
      }
    }

    const sidebarHtml = `
      <aside id="sidebar">
        <div class="sidebar-brand">
          <div class="brand-icon">DRS</div>
          <div class="brand-title">
            <span>Disaster Relief</span>
            <span class="brand-subtitle">Rescue Platform Web</span>
          </div>
        </div>
        <nav class="sidebar-nav">
          ${navHtml}
        </nav>
        <div class="sidebar-footer">
          <div>Distributed Computing • Team 4</div>
          <div style="font-size: 0.7rem; color: #475569; margin-top: 2px;">Spring Boot 3.3 • SSE Active</div>
        </div>
      </aside>
    `;

    // 2. Top Header HTML
    const isMock = new URLSearchParams(window.location.search).get('mock') === '1' || 
                   localStorage.getItem('drs_mock_mode') === 'true';

    const headerHtml = `
      <header id="top-header">
        <div class="header-left">
          <button class="mobile-menu-btn" id="sidebar-toggle-btn" aria-label="Toggle Navigation">☰</button>
          <div class="breadcrumb-trail">
            <span>DRS Console</span>
            <span class="breadcrumb-separator">/</span>
            <span class="breadcrumb-current">${activeItem.label}</span>
          </div>
        </div>
        <div class="header-right">
          <div id="sse-indicator" class="sse-status-pill">
            <span class="sse-pulse"></span>
            <span id="sse-status-text">Live Events Stream</span>
          </div>
          <button id="mock-toggle-btn" class="mock-badge" title="Toggle Mock Mode (?mock=1)">
            ${isMock ? '⚡ MOCK ACTIVE' : '🔌 LIVE API'}
          </button>
        </div>
      </header>
    `;

    // Inject into DOM
    const sidebarContainer = document.getElementById('sidebar-mount');
    if (sidebarContainer) {
      sidebarContainer.innerHTML = sidebarHtml;
    } else {
      document.body.insertAdjacentHTML('afterbegin', sidebarHtml);
    }

    const headerContainer = document.getElementById('header-mount');
    if (headerContainer) {
      headerContainer.innerHTML = headerHtml;
    }

    // Attach Toggle Listeners
    const toggleBtn = document.getElementById('sidebar-toggle-btn');
    const sidebar = document.getElementById('sidebar');
    if (toggleBtn && sidebar) {
      toggleBtn.addEventListener('click', () => {
        sidebar.classList.toggle('open');
      });
      // Close sidebar when clicking outside on mobile
      document.addEventListener('click', (e) => {
        if (sidebar.classList.contains('open') && 
            !sidebar.contains(e.target) && 
            e.target !== toggleBtn) {
          sidebar.classList.remove('open');
        }
      });
    }

    // Mock Mode Toggle
    const mockBtn = document.getElementById('mock-toggle-btn');
    if (mockBtn) {
      mockBtn.addEventListener('click', () => {
        const currentlyMock = localStorage.getItem('drs_mock_mode') === 'true' || 
                              new URLSearchParams(window.location.search).get('mock') === '1';
        const url = new URL(window.location.href);
        if (currentlyMock) {
          localStorage.removeItem('drs_mock_mode');
          url.searchParams.delete('mock');
        } else {
          localStorage.setItem('drs_mock_mode', 'true');
          url.searchParams.set('mock', '1');
        }
        window.location.href = url.toString();
      });
    }
  }

  // Automatically subscribe to critical distributed events for global toasts
  function registerGlobalToasts() {
    if (!window.events || window.__drs_global_toasts_registered) return;
    window.__drs_global_toasts_registered = true;

    // 1. NODE_SILENT (Exp 2 Heartbeats)
    window.events.on('core', 'NODE_SILENT', (payload) => {
      const id = payload?.nodeId || payload?.id || 'Edge-01';
      if (window.toast) {
        window.toast.warning('Node Silent Alert', `Node ${id} failed heartbeat check! Flagged for rescue inspection.`);
      }
    });

    // 2. LEADER_ELECTED (Exp 4 Consensus)
    window.events.on('consensus', 'LEADER_ELECTED', (payload) => {
      const algo = payload?.algorithm || 'Consensus';
      const leader = payload?.leaderId || payload?.id || 'Leader';
      const cluster = payload?.clusterId || 'Cluster-A';
      if (window.toast) {
        window.toast.info('Leader Elected', `${algo} elected ${leader} as coordinator for ${cluster}.`);
      }
    });

    // 3. RECONCILED (Exp 5 CRDT)
    window.events.on('consensus', 'RECONCILED', (payload) => {
      const count = payload?.totalUpdates || (Array.isArray(payload?.resolutions) ? payload.resolutions.length : 'All');
      const conflicts = payload?.conflictsDetected !== undefined ? payload.conflictsDetected : 0;
      if (window.toast) {
        window.toast.success('CRDT State Reconciled', `Reconciled ${count} updates via vector clocks (${conflicts} conflicts resolved).`);
      }
    });

    // 4. FT_BACKUP_PROMOTED (Exp 8 Primary-Backup Failover)
    window.events.on('ft', 'FT_BACKUP_PROMOTED', (payload) => {
      const backup = payload?.backupId || 'Backup-01';
      if (window.toast) {
        window.toast.error('Coordinator Failover', `Primary coordinator crashed! Hot-standby ${backup} promoted to active Primary.`);
      }
    });

    // 5. NETWORK_PARTITIONED (Exp 3 Topology Isolation)
    window.events.on('core', 'NETWORK_PARTITIONED', () => {
      if (window.toast) {
        window.toast.warning('Network Partitioned', 'Cluster isolation triggered. Local leader election required.');
      }
    });

    // 6. NETWORK_HEALED (Exp 3 Inter-cluster reconnection)
    window.events.on('core', 'NETWORK_HEALED', () => {
      if (window.toast) {
        window.toast.success('Network Partition Healed', 'Inter-cluster connectivity restored. Reconciling states...');
      }
    });
  }

  // Initialize on DOM Ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      initToastContainer();
      injectShell();
      registerGlobalToasts();
    });
  } else {
    initToastContainer();
    injectShell();
    registerGlobalToasts();
  }
})();
