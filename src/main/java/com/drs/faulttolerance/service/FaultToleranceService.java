package com.drs.faulttolerance.service;

import com.drs.core.event.EventPublisher;
import com.drs.faulttolerance.model.FaultToleranceResult;
import com.drs.faulttolerance.model.SystemUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Primary-Backup Replication Service — Spring port of legacy Experiment 8.
 * <p>
 * Ports {@code PrimaryCoordinator} + {@code BackupReplica} into a single
 * stateful service. Algorithm logic (replicate to backup, fail primary,
 * promote backup, process new updates after failover) is identical.
 * All {@code System.out.println} replaced by step traces + {@link EventPublisher}.
 */
@Service
public class FaultToleranceService {

    private static final Logger log = LoggerFactory.getLogger(FaultToleranceService.class);

    private final EventPublisher eventPublisher;
    private final AtomicInteger updateCounter = new AtomicInteger(0);

    // ── internal state (matches legacy PrimaryCoordinator + BackupReplica) ──

    private String primaryId = "Coordinator-Primary";
    private String backupId = "Coordinator-Backup";
    private boolean primaryActive = true;
    private boolean backupActive = true;
    private boolean backupPromoted = false;

    /** The replicated state, keyed by resourceId. */
    private final Map<String, SystemUpdate> replicatedState = new LinkedHashMap<>();

    public FaultToleranceService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    // ── operations ──

    /**
     * Process an update through the primary, replicating it to the backup.
     * Mirrors legacy {@code PrimaryCoordinator.processUpdate}.
     */
    public FaultToleranceResult processUpdate(String resourceId, String updateType,
                                              String value, String sourceNode) {
        List<String> steps = new ArrayList<>();
        String updateId = "UPDATE-" + updateCounter.incrementAndGet();
        SystemUpdate update = new SystemUpdate(updateId, resourceId, updateType, value, sourceNode);

        if (!primaryActive) {
            if (backupPromoted) {
                // After failover, backup processes new updates
                replicatedState.put(update.getResourceId(), update);
                steps.add("[" + backupId + "] New update processed after failover: " + update);
                log.info("Backup (promoted) processed update: {}", updateId);
            } else {
                steps.add("[" + primaryId + "] PRIMARY FAILED. Cannot process update.");
                steps.add("[" + backupId + "] Backup is not Primary yet.");
                log.warn("Update rejected: primary failed and backup not promoted");
            }
        } else {
            steps.add("[" + primaryId + "] Processing system update: " + update);
            replicatedState.put(update.getResourceId(), update);
            steps.add("[" + primaryId + "] Update successfully replicated.");
            log.info("Primary processed and replicated update: {}", updateId);
            eventPublisher.publish("ft", "FT_REPLICATED", Map.of(
                    "updateId", updateId, "resourceId", resourceId, "value", value));
        }

        return buildResult(steps);
    }

    /**
     * Simulate a primary failure.
     * Mirrors legacy {@code PrimaryCoordinator.fail()}.
     */
    public FaultToleranceResult failPrimary() {
        List<String> steps = new ArrayList<>();
        primaryActive = false;

        steps.add("       PRIMARY FAILURE        ");
        steps.add("[" + primaryId + "] Coordinator has FAILED.");
        steps.add("System will perform failover...");

        log.info("Primary {} has FAILED", primaryId);
        eventPublisher.publish("ft", "FT_PRIMARY_FAILED", Map.of("primaryId", primaryId));

        // Auto-promote backup
        if (backupActive) {
            backupPromoted = true;
            steps.add("[" + backupId + "] BACKUP PROMOTED TO PRIMARY");
            log.info("Backup {} promoted to primary", backupId);
            eventPublisher.publish("ft", "FT_BACKUP_PROMOTED", Map.of("backupId", backupId));
        } else {
            steps.add("[" + backupId + "] Cannot promote. Backup is failed.");
        }

        return buildResult(steps);
    }

    /** Get the current system state without any mutation. */
    public FaultToleranceResult getState() {
        List<String> steps = new ArrayList<>();
        steps.add("[" + (backupPromoted ? backupId : primaryId) + "] Current System State");
        if (replicatedState.isEmpty()) {
            steps.add("No system updates available.");
        } else {
            for (SystemUpdate u : replicatedState.values()) {
                steps.add(u.toString());
            }
        }
        steps.add("Primary Status: " + (primaryActive ? "ACTIVE" : "FAILED"));
        steps.add("Backup Status: " + (backupPromoted ? "ACTIVE PRIMARY" : "STANDBY"));
        return buildResult(steps);
    }

    /** Reset the entire fault-tolerance subsystem to its initial state. */
    public FaultToleranceResult reset() {
        primaryActive = true;
        backupActive = true;
        backupPromoted = false;
        replicatedState.clear();
        updateCounter.set(0);

        List<String> steps = new ArrayList<>();
        steps.add("Fault-tolerance system reset to initial state.");
        steps.add("Primary: " + primaryId + " (ACTIVE)");
        steps.add("Backup: " + backupId + " (STANDBY)");

        log.info("Fault-tolerance system reset");
        return buildResult(steps);
    }

    // ── helpers ──

    private FaultToleranceResult buildResult(List<String> steps) {
        return new FaultToleranceResult(
                primaryId, backupId, primaryActive, backupPromoted,
                Collections.unmodifiableMap(new LinkedHashMap<>(replicatedState)),
                steps);
    }
}
