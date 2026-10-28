package com.drs.faulttolerance.service;

import com.drs.core.event.EventPublisher;
import com.drs.faulttolerance.model.FaultToleranceResult;
import com.drs.faulttolerance.model.PrimaryCoordinator;
import com.drs.faulttolerance.model.BackupReplica;
import com.drs.faulttolerance.model.SystemUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReplicationService}, {@link PrimaryCoordinator}, and
 * {@link BackupReplica} (Exp 8).
 */
@ExtendWith(MockitoExtension.class)
class ReplicationServiceTest {

    @Mock
    EventPublisher eventPublisher;

    ReplicationService service;

    @BeforeEach
    void setUp() {
        service = new ReplicationService(eventPublisher);
    }

    @Test
    @DisplayName("Update is replicated to backup while primary is active")
    void updateReplicatedToBackup() {
        FaultToleranceResult result = service.processUpdate(
                "House-12", "STATUS", "NEEDS_RESCUE", "UAV-01");

        assertTrue(result.isPrimaryActive());
        assertFalse(result.isBackupPromoted());
        assertEquals(1, result.getCurrentState().size());
        assertTrue(result.getCurrentState().containsKey("House-12"));
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("successfully replicated")));

        // Verify live event publication
        verify(eventPublisher, times(1)).publish(eq("ft"), eq("FT_REPLICATED"), any());
    }

    @Test
    @DisplayName("Primary fails â†’ marks primary inactive and backup promoted")
    void primaryFailsBackupPromoted() {
        service.processUpdate("House-12", "STATUS", "NEEDS_RESCUE", "UAV-01");
        FaultToleranceResult result = service.failPrimary();

        assertFalse(result.isPrimaryActive());
        assertTrue(result.isBackupPromoted());
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("PRIMARY FAILURE")));
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("BACKUP PROMOTED TO PRIMARY")));

        verify(eventPublisher).publish(eq("ft"), eq("FT_PRIMARY_FAILED"), any());
        verify(eventPublisher).publish(eq("ft"), eq("FT_BACKUP_PROMOTED"), any());
    }

    @Test
    @DisplayName("Later updates processed by promoted backup node after failover")
    void laterUpdatesProcessedByPromotedNode() {
        service.processUpdate("House-12", "STATUS", "NEEDS_RESCUE", "UAV-01");
        service.failPrimary();

        FaultToleranceResult result = service.processUpdate(
                "School-07", "STATUS", "NEEDS_RESCUE", "UAV-03");

        assertEquals(2, result.getCurrentState().size());
        assertTrue(result.getCurrentState().containsKey("House-12"));
        assertTrue(result.getCurrentState().containsKey("School-07"));
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("after failover")));
    }

    @Test
    @DisplayName("Direct model test: PrimaryCoordinator replicates to BackupReplica")
    void directModelTest() {
        BackupReplica backup = new BackupReplica("Backup-Test");
        PrimaryCoordinator primary = new PrimaryCoordinator("Primary-Test", backup);

        SystemUpdate u1 = new SystemUpdate("U-1", "H-1", "STATUS", "RESCUED", "UAV-01");
        List<String> steps = new ArrayList<>();
        boolean ok = primary.processUpdate(u1, steps);

        assertTrue(ok);
        assertEquals(1, backup.getState().size());
        assertEquals(u1, backup.getState().get("H-1"));

        // Fail primary
        primary.fail(steps);
        assertFalse(primary.isActive());

        // Further primary updates rejected
        SystemUpdate u2 = new SystemUpdate("U-2", "H-2", "STATUS", "OPEN", "UAV-02");
        assertFalse(primary.processUpdate(u2, steps));

        // Promote backup and process
        backup.promoteToPrimary(steps);
        assertTrue(backup.isPromoted());
        assertTrue(backup.processNewUpdate(u2, steps));
        assertEquals(2, backup.getState().size());
    }
}
