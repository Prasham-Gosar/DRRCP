package com.drs.faulttolerance.model;

import java.util.List;

/**
 * Primary coordinator in the primary-backup fault tolerance scheme (Exp 8).
 * <p>
 * Ported from legacy {@code Experiment8.PrimaryCoordinator}.
 * All console prints replaced with structured return values and step trace
 * appends.
 */
public class PrimaryCoordinator {

    private final String nodeId;
    private boolean active;
    private final BackupReplica backup;

    public PrimaryCoordinator(String nodeId, BackupReplica backup) {
        this.nodeId = nodeId;
        this.backup = backup;
        this.active = true;
    }

    /**
     * Process a system update and replicate it immediately to the backup.
     *
     * @param update the update to process
     * @param steps  trace collector for execution steps
     * @return true if successfully processed and replicated; false if primary is
     *         failed
     */
    public boolean processUpdate(SystemUpdate update, List<String> steps) {
        if (!active) {
            if (steps != null) {
                steps.add("[" + nodeId + "] PRIMARY FAILED. Cannot process update.");
            }
            return false;
        }

        if (steps != null) {
            steps.add("[" + nodeId + "] Processing system update: " + update);
        }

        // Replicate the update immediately to the Backup.
        boolean replicated = backup.replicate(update, steps);
        if (replicated && steps != null) {
            steps.add("[" + nodeId + "] Update successfully replicated.");
        }
        return replicated;
    }

    /**
     * Overload for convenience without step collector.
     */
    public boolean processUpdate(SystemUpdate update) {
        return processUpdate(update, null);
    }

    /**
     * Simulate failure of the Primary.
     *
     * @param steps trace collector
     */
    public void fail(List<String> steps) {
        active = false;
        if (steps != null) {
            steps.add("       PRIMARY FAILURE        ");
            steps.add("[" + nodeId + "] Coordinator has FAILED.");
            steps.add("System will perform failover...");
        }
    }

    public void fail() {
        fail(null);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getNodeId() {
        return nodeId;
    }

    public BackupReplica getBackup() {
        return backup;
    }
}
