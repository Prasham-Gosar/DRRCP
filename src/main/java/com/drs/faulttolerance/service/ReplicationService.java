package com.drs.faulttolerance.service;

import com.drs.core.event.EventPublisher;
import com.drs.faulttolerance.model.BackupReplica;
import com.drs.faulttolerance.model.FaultToleranceResult;
import com.drs.faulttolerance.model.PrimaryCoordinator;
import com.drs.faulttolerance.model.SystemUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Primary-Backup Replication Service — Spring service port of legacy Experiment
 * 8.
 * <p>
 * Holds one {@link PrimaryCoordinator} and one {@link BackupReplica}.
 * Coordinates update processing, synchronous replication, failover promotion,
 * and live event notifications via {@link EventPublisher}.
 */
@Service
public class ReplicationService {

    private static final Logger log = LoggerFactory.getLogger(ReplicationService.class);

    private final EventPublisher eventPublisher;
    private final AtomicInteger updateCounter = new AtomicInteger(0);

    private PrimaryCoordinator primary;
    private BackupReplica backup;

    public ReplicationService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        initNodes();
    }

    private void initNodes() {
        this.backup = new BackupReplica("Coordinator-Backup");
        this.primary = new PrimaryCoordinator("Coordinator-Primary", this.backup);
    }

    /**
     * Process an update through the primary, replicating it synchronously to
     * backup.
     * If the primary has failed and backup is promoted, the update is processed
     * directly by the promoted backup.
     */
    public FaultToleranceResult processUpdate(String resourceId, String updateType,
            String value, String sourceNode) {
        List<String> steps = new ArrayList<>();
        String updateId = "UPDATE-" + updateCounter.incrementAndGet();
        SystemUpdate update = new SystemUpdate(updateId, resourceId, updateType, value, sourceNode);

        if (!primary.isActive()) {
            if (backup.isPromoted()) {
                backup.processNewUpdate(update, steps);
                log.info("Backup (promoted) processed update: {}", updateId);
            } else {
                steps.add("[" + primary.getNodeId() + "] PRIMARY FAILED. Cannot process update.");
                steps.add("[" + backup.getNodeId() + "] Backup is not Primary yet.");
                log.warn("Update rejected: primary failed and backup not promoted");
            }
        } else {
            boolean replicated = primary.processUpdate(update, steps);
            if (replicated) {
                log.info("Primary processed and replicated update: {}", updateId);
                eventPublisher.publish("ft", "FT_REPLICATED", Map.of(
                        "updateId", updateId, "resourceId", resourceId, "value", value));
            }
        }

        return buildResult(steps);
    }

    /**
     * Simulate primary coordinator failure and promote backup.
     */
    public FaultToleranceResult failPrimary() {
        List<String> steps = new ArrayList<>();
        primary.fail(steps);
        log.info("Primary {} has FAILED", primary.getNodeId());
        eventPublisher.publish("ft", "FT_PRIMARY_FAILED", Map.of("primaryId", primary.getNodeId()));

        if (backup.isActive()) {
            backup.promoteToPrimary(steps);
            log.info("Backup {} promoted to primary", backup.getNodeId());
            eventPublisher.publish("ft", "FT_BACKUP_PROMOTED", Map.of("backupId", backup.getNodeId()));
        } else {
            steps.add("[" + backup.getNodeId() + "] Cannot promote. Backup is failed.");
        }

        return buildResult(steps);
    }

    /**
     * Retrieve the current system state.
     */
    public FaultToleranceResult getState() {
        List<String> steps = new ArrayList<>();
        String activeLeader = backup.isPromoted() ? backup.getNodeId() : primary.getNodeId();
        steps.add("[" + activeLeader + "] Current System State");

        Map<String, SystemUpdate> state = backup.getState();
        if (state.isEmpty()) {
            steps.add("No system updates available.");
        } else {
            for (SystemUpdate u : state.values()) {
                steps.add(u.toString());
            }
        }
        steps.add("Primary Status: " + (primary.isActive() ? "ACTIVE" : "FAILED"));
        steps.add("Backup Status: " + (backup.isPromoted() ? "ACTIVE PRIMARY" : "STANDBY"));

        return buildResult(steps);
    }

    /**
     * Reset the replication subsystem back to initial state.
     */
    public FaultToleranceResult reset() {
        initNodes();
        updateCounter.set(0);

        List<String> steps = new ArrayList<>();
        steps.add("Fault-tolerance system reset to initial state.");
        steps.add("Primary: " + primary.getNodeId() + " (ACTIVE)");
        steps.add("Backup: " + backup.getNodeId() + " (STANDBY)");

        log.info("Fault-tolerance system reset");
        return buildResult(steps);
    }

    public PrimaryCoordinator getPrimary() {
        return primary;
    }

    public BackupReplica getBackup() {
        return backup;
    }

    private FaultToleranceResult buildResult(List<String> steps) {
        return new FaultToleranceResult(
                primary.getNodeId(),
                backup.getNodeId(),
                primary.isActive(),
                backup.isPromoted(),
                backup.getState(),
                steps);
    }
}
