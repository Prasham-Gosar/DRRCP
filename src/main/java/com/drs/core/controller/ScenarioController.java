package com.drs.core.controller;

import com.drs.consensus.model.ReconciliationResult;
import com.drs.core.scenario.ScenarioConfig;
import com.drs.core.scenario.ScenarioRunner;
import com.drs.core.scenario.ScenarioStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Exp 1-5 End-to-End Scenario Runner.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code POST /api/scenario/run} — trigger asynchronous execution of the full story</li>
 *   <li>{@code GET  /api/scenario/status} — query current phase, step telemetry, and reconciliation results</li>
 *   <li>{@code POST /api/scenario/reset} — reset scenario state</li>
 *   <li>{@code GET  /api/reconcile/latest} — convenience endpoint providing latest reconciliation summary</li>
 * </ul>
 */
@RestController
public class ScenarioController {

    private final ScenarioRunner scenarioRunner;

    public ScenarioController(ScenarioRunner scenarioRunner) {
        this.scenarioRunner = scenarioRunner;
    }

    @PostMapping("/api/scenario/run")
    public ResponseEntity<?> runScenario(@RequestBody(required = false) ScenarioConfig config) {
        try {
            scenarioRunner.runScenario(config);
            return ResponseEntity.accepted().body(scenarioRunner.getStatus());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(java.util.Map.of(
                    "error", e.getMessage(),
                    "status", scenarioRunner.getStatus()
            ));
        }
    }

    @GetMapping("/api/scenario/status")
    public ScenarioStatus getStatus() {
        return scenarioRunner.getStatus();
    }

    @PostMapping("/api/scenario/reset")
    public ResponseEntity<?> resetScenario() {
        try {
            return ResponseEntity.ok(scenarioRunner.reset());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /**
     * Fallback / convenience endpoint for /api/reconcile/latest.
     * Integrates with Grasit's ReconciliationService to return the latest reconciliation result.
     */
    @GetMapping("/api/reconcile/latest")
    public ResponseEntity<?> getLatestReconciliation() {
        ReconciliationResult result = scenarioRunner.getLastReconciliation();
        if (result == null) {
            return ResponseEntity.ok(java.util.Map.of(
                    "message", "No reconciliation has been executed yet. Run the scenario or call /api/reconcile.",
                    "resolutions", java.util.List.of(),
                    "totalUpdates", 0,
                    "conflictsDetected", 0,
                    "humanReviewRequired", 0
            ));
        }
        return ResponseEntity.ok(result);
    }
}

