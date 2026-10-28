/**
 * api.js - Unified Fetch Wrapper with Automatic Mock Mode and Error Toasting
 * Author: Vedant (Frontend Shell)
 *
 * Usage:
 *   const data = await api.get('/api/nodes');
 *   const result = await api.post('/api/comm/run-all', { numProcesses: 4 });
 *   await api.del('/api/updates/1');
 *
 * Mock Mode:
 *   Add ?mock=1 to the URL or click the toggle in the header.
 *   api.js will intercept requests and serve mock JSON from /mock/*.json.
 */

(function () {
  'use strict';

  // Endpoint to Mock file mapping table
  const MOCK_ROUTES = {
    // Core & Overview
    '/api/overview': '/mock/overview.json',
    '/api/nodes': '/mock/nodes.json',
    '/api/coordinator/nodes': '/mock/nodes.json',
    '/api/events/history': '/mock/events_history.json',

    // Collective Comms (Exp 9)
    '/api/comm/run-all': '/mock/comm_result.json',
    '/api/comm/bcast': '/mock/comm_result.json',
    '/api/comm/scatter': '/mock/comm_result.json',
    '/api/comm/gather': '/mock/comm_result.json',

    // Consensus & Fault Tolerance (Exp 4, 5, 8)
    '/api/election/bully': '/mock/election_bully.json',
    '/api/election/ring': '/mock/election_bully.json',
    '/api/election/compare': '/mock/election_bully.json',
    '/api/election/history': '/mock/election_history.json',
    '/api/election/leader': '/mock/election_bully.json',
    '/api/reconcile': '/mock/reconcile.json',
    '/api/reconcile/latest': '/mock/reconcile.json',
    '/api/ft/state': '/mock/ft_state.json',
    '/api/ft/updates': '/mock/ft_state.json',
    '/api/ft/primary/fail': '/mock/ft_state.json',
    '/api/ft/reset': '/mock/ft_state.json',

    // Compute & Load Balancer (Exp 6, 7)
    '/api/loadbalancer/nodes': '/mock/loadbalancer.json',
    '/api/loadbalancer/assign-all': '/mock/loadbalancer.json'
  };

  class ApiClient {
    constructor() {
      this.mockParam = new URLSearchParams(window.location.search).get('mock') === '1';
      this.mockStorage = localStorage.getItem('drs_mock_mode') === 'true';
    }

    isMock() {
      return this.mockParam || this.mockStorage;
    }

    /**
     * Resolves mock path or returns null if not mocked
     */
    resolveMockPath(path) {
      if (!this.isMock()) return null;

      // Strip query parameters for route matching
      const cleanPath = path.split('?')[0].replace(/\/+$/, '');
      if (MOCK_ROUTES[cleanPath]) {
        return MOCK_ROUTES[cleanPath];
      }

      // Prefix match fallbacks
      for (const [route, mockFile] of Object.entries(MOCK_ROUTES)) {
        if (cleanPath.startsWith(route)) {
          return mockFile;
        }
      }
      return null;
    }

    async request(method, path, body = null, options = {}) {
      const mockFile = this.resolveMockPath(path);

      if (mockFile) {
        console.info(`[API Mock] Serving ${method} ${path} from ${mockFile}`);
        // Simulate small network delay (120ms) for realistic UI response
        await new Promise(r => setTimeout(r, 120));
        try {
          const res = await fetch(mockFile);
          if (!res.ok) {
            throw new Error(`Mock file ${mockFile} not found (${res.status})`);
          }
          return await res.json();
        } catch (err) {
          this.handleError(`Mock Request Failed: ${path}`, err.message);
          throw err;
        }
      }

      // Live Backend Request
      const fetchOptions = {
        method: method.toUpperCase(),
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
          ...(options.headers || {})
        },
        ...options
      };

      if (body !== null && method.toUpperCase() !== 'GET') {
        fetchOptions.body = JSON.stringify(body);
      }

      try {
        const response = await fetch(path, fetchOptions);

        if (!response.ok) {
          let errorText = `HTTP ${response.status}: ${response.statusText}`;
          try {
            const errJson = await response.json();
            if (errJson.message) errorText = errJson.message;
            else if (errJson.error) errorText = errJson.error;
          } catch (_) {
            const txt = await response.text();
            if (txt) errorText = txt;
          }
          throw new Error(errorText);
        }

        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('application/json')) {
          return await response.json();
        }
        return await response.text();
      } catch (err) {
        this.handleError(`API Error (${method} ${path})`, err.message);
        throw err;
      }
    }

    get(path, options = {}) {
      return this.request('GET', path, null, options);
    }

    post(path, body = {}, options = {}) {
      return this.request('POST', path, body, options);
    }

    del(path, options = {}) {
      return this.request('DELETE', path, null, options);
    }

    handleError(title, message) {
      console.error(`[API Error] ${title}:`, message);
      if (window.toast && typeof window.toast.error === 'function') {
        window.toast.error(title, message);
      }
    }
  }

  window.api = new ApiClient();
})();
