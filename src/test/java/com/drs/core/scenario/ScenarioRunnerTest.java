package com.drs.core.scenario;

import com.drs.consensus.model.ReconciliationResult;
import com.drs.consensus.model.Resolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ScenarioRunnerTest {

    @Autowired
    private ScenarioRunner scenarioRunner;

    @Test
    @DisplayName("Exp 1-5 Full Story: Run pipeline end-to-end and assert NEEDS_RESCUE wins safety-first resolution")
    void testFullScenarioExecution() throws Exception {
        ScenarioConfig config = new ScenarioConfig();
        config.setFastMode(true); // 0ms delay for fast test execution

        CompletableFuture<ScenarioStatus> future = scenarioRunner.runScenario(config);
        ScenarioStatus result = future.get(20, TimeUnit.SECONDS);

        // 1. Pipeline status checks
        assertNotNull(result);
        assertEquals("COMPLETED", result.getState());
        assertEquals(7, result.getCurrentPhase());
        assertNull(result.getErrorMessage());

        // 2. Verify all 7 phases executed successfully
        assertEquals(7, result.getPhases().size());
        for (PhaseDetail phase : result.getPhases()) {
            assertEquals("COMPLETED", phase.getStatus(), "Phase " + phase.getPhaseNumber() + " should be COMPLETED");
            assertFalse(phase.getSteps().isEmpty(), "Phase " + phase.getPhaseNumber() + " should have execution steps");
        }

        // 3. Verify Exp 5 Reconciliation Result
        ReconciliationResult rec = result.getReconciliation();
        assertNotNull(rec, "Reconciliation result must be present upon scenario completion");
        assertTrue(rec.getTotalUpdates() >= 4, "Should reconcile all 4 disaster updates");
        assertEquals(1, rec.getConflictsDetected(), "Should detect 1 conflict on House-12");
        assertEquals(1, rec.getHumanReviewRequired(), "Should flag House-12 conflict for human review");

        // 4. Assert House-12: NEEDS_RESCUE wins over RESCUED
        Resolution house12 = rec.getResolutions().stream()
                .filter(r -> "House-12".equals(r.getResourceKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Resolution for House-12 not found"));

        assertEquals("NEEDS_RESCUE", house12.getFinalValue(),
                "Safety-first policy requires NEEDS_RESCUE to win over RESCUED");
        assertEquals("POLICY_RESOLVED", house12.getMethod());
        assertTrue(house12.isNeedsHumanReview(), "Conflicting distress signal must be flagged for human review");
        assertTrue(house12.getExplanation().contains("Safety-first policy applied"));

        // 5. Assert confirmedSurvivors: CRDT grow-only set union
        Resolution survivors = rec.getResolutions().stream()
                .filter(r -> "confirmedSurvivors".equals(r.getResourceKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Resolution for confirmedSurvivors not found"));

        assertEquals("CRDT_MERGED", survivors.getMethod());
        assertFalse(survivors.isNeedsHumanReview());
        assertTrue(survivors.getFinalValue().contains("Meera Joshi"), "Survivors union must include Meera Joshi");
        assertTrue(survivors.getFinalValue().contains("Raghav Iyer"), "Survivors union must include Raghav Iyer");
    }

    @Test
    @DisplayName("Stability QA: Run scenario 5 times consecutively to verify zero flakiness and deterministic safety-first resolution")
    void testScenarioRunnerStabilityRepeatedFiveTimes() throws Exception {
        ScenarioConfig config = new ScenarioConfig();
        config.setFastMode(true);

        for (int run = 1; run <= 5; run++) {
            scenarioRunner.reset();
            CompletableFuture<ScenarioStatus> future = scenarioRunner.runScenario(config);
            ScenarioStatus result = future.get(15, TimeUnit.SECONDS);

            assertNotNull(result, "Run " + run + ": result should not be null");
            assertEquals("COMPLETED", result.getState(), "Run " + run + ": state should be COMPLETED");
            assertEquals(7, result.getCurrentPhase(), "Run " + run + ": all 7 phases must complete");
            assertNull(result.getErrorMessage(), "Run " + run + ": error message must be null");

            ReconciliationResult rec = result.getReconciliation();
            assertNotNull(rec, "Run " + run + ": reconciliation result must be present");
            assertEquals(1, rec.getConflictsDetected(), "Run " + run + ": exactly 1 conflict expected");

            Resolution house12 = rec.getResolutions().stream()
                    .filter(r -> "House-12".equals(r.getResourceKey()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Run " + run + ": House-12 resolution missing"));

            assertEquals("NEEDS_RESCUE", house12.getFinalValue(),
                    "Run " + run + ": Safety-first policy requires NEEDS_RESCUE to always win");
        }
    }
}

