package com.drs.core.controller;

import com.drs.consensus.model.ReconciliationResult;
import com.drs.core.scenario.ScenarioConfig;
import com.drs.core.scenario.ScenarioRunner;
import com.drs.core.scenario.ScenarioStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * REST controller for Exp Full Story: orchestrating the 7-phase disaster relief scenario.
 * Supports /api/scenario/run, /api/scenario/status, /api/scenario/reset.
 */
@RestController
public class ScenarioController {

    private final ScenarioRunner scenarioRunner;

    public ScenarioController(ScenarioRunner scenarioRunner) {
        this.scenarioRunner = scenarioRunner;
    }

    @PostMapping("/api/scenario/run")
    public ResponseEntity<?> runScenario(@RequestBody(required = false) ScenarioConfig config) {
        if (config == null) {
            config = new ScenarioConfig();
        }
        try {
            CompletableFuture<ScenarioStatus> future = scenarioRunner.runScenario(config);
            // Return 202 Accepted with immediate status snapshot
            return ResponseEntity.accepted().body(scenarioRunner.getStatus());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(java.util.Map.of("error", e.getMessage()));
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
     * Scenario-specific reconciliation endpoint at /api/scenario/reconcile/latest.
     */
    @GetMapping("/api/scenario/reconcile/latest")
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
