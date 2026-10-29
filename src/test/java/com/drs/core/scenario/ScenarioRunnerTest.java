package com.drs.core.scenario;

import com.drs.consensus.model.ReconciliationResult;
import com.drs.consensus.model.Resolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ScenarioRunnerTest {

    @Autowired
    private ScenarioRunner scenarioRunner;

    @Test
    @DisplayName("End-to-end full scenario run in fast mode must complete all 7 phases and reconcile state")
    void testFullScenarioExecution() throws Exception {
        ScenarioConfig config = new ScenarioConfig();
        config.setFastMode(true);
        config.setClockSyncDriftMs(120);

        CompletableFuture<ScenarioStatus> future = scenarioRunner.runScenario(config);
        assertNotNull(future);

        ScenarioStatus result = future.get(15, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals("COMPLETED", result.getState());
        assertEquals(7, result.getCurrentPhase());
        assertNull(result.getErrorMessage());

        // Check phase history
        assertEquals(7, result.getPhases().size());
        assertTrue(result.getPhases().stream().allMatch(p -> "COMPLETED".equals(p.getState())));

        // Check reconciliation result
        ReconciliationResult rec = result.getReconciliation();
        assertNotNull(rec, "Reconciliation result must be populated in Phase 7");
        assertTrue(rec.getConflictsDetected() > 0, "Concurrent conflict for House-12 must be detected");
        assertTrue(rec.getResolutions().size() >= 2, "Must resolve at least House-12 and Sector-4-Shelter");

        Resolution house12 = rec.getResolutions().stream()
                .filter(r -> "House-12".equals(r.getResourceKey()))
                .findFirst()
                .orElse(null);

        assertNotNull(house12);
        assertEquals("NEEDS_RESCUE", house12.getFinalValue(),
                "Safety-first precedence must choose NEEDS_RESCUE over EVACUATED");
    }

    @Test
    @DisplayName("Resetting ScenarioRunner clears state and resets currentPhase to 0")
    void testScenarioReset() {
        ScenarioConfig config = new ScenarioConfig();
        config.setFastMode(true);

        scenarioRunner.reset();
        ScenarioStatus status = scenarioRunner.getStatus();

        assertNotNull(status);
        assertEquals("IDLE", status.getState());
        assertEquals(0, status.getCurrentPhase());
        assertNull(status.getReconciliation());
        assertEquals(7, status.getPhases().size()); assertTrue(status.getPhases().stream().allMatch(p -> "PENDING".equals(p.getStatus())));
    }

    @Test
    @DisplayName("Stability QA: Run scenario 5 times consecutively to verify zero flakiness and deterministic safety-first resolution")
    void testScenarioRunnerStabilityRepeatedFiveTimes() throws Exception {
        ScenarioConfig config = new ScenarioConfig();
        config.setFastMode(true);

        for (int run = 1; run <= 5; run++) {
            final int currentRun = run;
            scenarioRunner.reset();
            CompletableFuture<ScenarioStatus> future = scenarioRunner.runScenario(config);
            ScenarioStatus result = future.get(15, TimeUnit.SECONDS);

            assertNotNull(result, "Run " + currentRun + ": result should not be null");
            assertEquals("COMPLETED", result.getState(), "Run " + currentRun + ": state should be COMPLETED");
            assertEquals(7, result.getCurrentPhase(), "Run " + currentRun + ": all 7 phases must complete");
            assertNull(result.getErrorMessage(), "Run " + currentRun + ": error message must be null");

            ReconciliationResult rec = result.getReconciliation();
            assertNotNull(rec, "Run " + currentRun + ": reconciliation result must be present");
            assertEquals(1, rec.getConflictsDetected(), "Run " + currentRun + ": exactly 1 conflict expected");

            Resolution house12 = rec.getResolutions().stream()
                    .filter(r -> "House-12".equals(r.getResourceKey()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Run " + currentRun + ": House-12 resolution missing"));

            assertEquals("NEEDS_RESCUE", house12.getFinalValue(),
                    "Run " + currentRun + ": Safety-first policy requires NEEDS_RESCUE to always win");
        }
    }
}
