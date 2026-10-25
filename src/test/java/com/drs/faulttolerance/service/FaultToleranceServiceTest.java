package com.drs.faulttolerance.service;

import com.drs.core.event.EventPublisher;
import com.drs.faulttolerance.model.FaultToleranceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FaultToleranceService} — primary-backup replication (Exp 8).
 */
@ExtendWith(MockitoExtension.class)
class FaultToleranceServiceTest {

    @Mock
    EventPublisher eventPublisher;

    FaultToleranceService service;

    @BeforeEach
    void setUp() {
        service = new FaultToleranceService(eventPublisher);
    }

    @Test
    @DisplayName("Process update while primary is active → replicated")
    void processWhileActive() {
        FaultToleranceResult result = service.processUpdate(
                "House-12", "STATUS", "NEEDS_RESCUE", "UAV-01");
        assertTrue(result.isPrimaryActive());
        assertFalse(result.isBackupPromoted());
        assertEquals(1, result.getCurrentState().size());
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("replicated")));
    }

    @Test
    @DisplayName("Fail primary → primary inactive, backup promoted")
    void failPrimary() {
        service.processUpdate("House-12", "STATUS", "NEEDS_RESCUE", "UAV-01");
        FaultToleranceResult result = service.failPrimary();
        assertFalse(result.isPrimaryActive());
        assertTrue(result.isBackupPromoted());
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("FAILED")));
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("PROMOTED")));
    }

    @Test
    @DisplayName("Process update after failover → handled by backup-as-primary")
    void processAfterFailover() {
        service.processUpdate("House-12", "STATUS", "NEEDS_RESCUE", "UAV-01");
        service.failPrimary();
        FaultToleranceResult result = service.processUpdate(
                "School-07", "STATUS", "NEEDS_RESCUE", "UAV-03");
        assertEquals(2, result.getCurrentState().size());
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("after failover")));
    }

    @Test
    @DisplayName("Previously replicated updates are preserved after failover")
    void replicatedDataPreserved() {
        service.processUpdate("House-12", "STATUS", "NEEDS_RESCUE", "UAV-01");
        service.processUpdate("Hospital-01", "STATUS", "MEDICAL_ASSISTANCE", "UAV-02");
        assertEquals(2, service.getState().getCurrentState().size());

        service.failPrimary();

        // Data still there after failover
        FaultToleranceResult state = service.getState();
        assertEquals(2, state.getCurrentState().size());
        assertTrue(state.getCurrentState().containsKey("House-12"));
        assertTrue(state.getCurrentState().containsKey("Hospital-01"));
    }

    @Test
    @DisplayName("Reset restores initial state")
    void reset() {
        service.processUpdate("House-12", "STATUS", "NEEDS_RESCUE", "UAV-01");
        service.failPrimary();
        FaultToleranceResult result = service.reset();
        assertTrue(result.isPrimaryActive());
        assertFalse(result.isBackupPromoted());
        assertTrue(result.getCurrentState().isEmpty());
    }

    @Test
    @DisplayName("Full Exp 8 scenario from the legacy demo")
    void fullScenario() {
        // Step 2: Normal updates
        service.processUpdate("House-12", "STATUS", "NEEDS_RESCUE", "UAV-01");
        service.processUpdate("Hospital-01", "STATUS", "MEDICAL_ASSISTANCE", "UAV-02");
        service.processUpdate("Shelter-03", "STATUS", "OPEN", "Edge-01");
        service.processUpdate("House-15", "STATUS", "RESCUED", "UAV-01");

        // Step 3: Backup has 4 entries
        assertEquals(4, service.getState().getCurrentState().size());

        // Step 4: Primary fails
        service.failPrimary();

        // Step 5: Try sending to failed primary
        FaultToleranceResult failedAttempt = service.processUpdate(
                "Bridge-02", "STATUS", "BLOCKED", "Edge-01");
        // Since backup is auto-promoted, this should succeed
        assertEquals(5, failedAttempt.getCurrentState().size());

        // Step 6: More updates after failover
        service.processUpdate("School-07", "STATUS", "NEEDS_RESCUE", "UAV-03");
        service.processUpdate("House-20", "STATUS", "RESCUED", "UAV-04");

        // Step 7: Final state
        FaultToleranceResult finalState = service.getState();
        assertFalse(finalState.isPrimaryActive());
        assertTrue(finalState.isBackupPromoted());
        assertEquals(7, finalState.getCurrentState().size());
    }
}
