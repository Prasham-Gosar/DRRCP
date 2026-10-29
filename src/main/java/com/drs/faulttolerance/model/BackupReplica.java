package com.drs.faulttolerance.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backup replica in the primary-backup fault tolerance scheme (Exp 8).
 * <p>
 * Ported from legacy {@code Experiment8.BackupReplica}.
 * All console prints replaced with structured return values and step trace
 * appends.
 */
public class BackupReplica {

    private final String nodeId;
    private boolean active;
    private boolean promoted;
    private final Map<String, SystemUpdate> replicatedUpdates;

    public BackupReplica(String nodeId) {
        this.nodeId = nodeId;
        this.active = true;
        this.promoted = false;
        this.replicatedUpdates = new LinkedHashMap<>();
    }

    /**
     * Receives and stores an update synchronously from the Primary.
     */
    public synchronized boolean replicate(SystemUpdate update, List<String> steps) {
        if (!active) {
            if (steps != null) {
                steps.add("[" + nodeId + "] Backup is unavailable.");
            }
            return false;
        }

        replicatedUpdates.put(update.getResourceId(), update);
        if (steps != null) {
            steps.add("[" + nodeId + "] Replicated update: " + update);
        }
        return true;
    }

    public synchronized boolean replicate(SystemUpdate update) {
        return replicate(update, null);
    }

    /**
     * Backup becomes the new Primary after primary failure.
     */
    public boolean promoteToPrimary(List<String> steps) {
        if (!active) {
            if (steps != null) {
                steps.add("[" + nodeId + "] Cannot promote. Backup is failed.");
            }
            return false;
        }

        promoted = true;
        if (steps != null) {
            steps.add("[" + nodeId + "] BACKUP PROMOTED TO PRIMARY");
        }
        return true;
    }

    public boolean promoteToPrimary() {
        return promoteToPrimary(null);
    }

    /**
     * Process a new system update after takeover / promotion.
     */
    public synchronized boolean processNewUpdate(SystemUpdate update, List<String> steps) {
        if (!active) {
            if (steps != null) {
                steps.add("[" + nodeId + "] Node is unavailable.");
            }
            return false;
        }

        if (!promoted) {
            if (steps != null) {
                steps.add("[" + nodeId + "] Backup is not Primary yet.");
            }
            return false;
        }

        replicatedUpdates.put(update.getResourceId(), update);
        if (steps != null) {
            steps.add("[" + nodeId + "] New update processed after failover: " + update);
        }
        return true;
    }

    public synchronized boolean processNewUpdate(SystemUpdate update) {
        return processNewUpdate(update, null);
    }

    public Map<String, SystemUpdate> getState() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(replicatedUpdates));
    }

    public boolean isPromoted() {
        return promoted;
    }

    public void setPromoted(boolean promoted) {
        this.promoted = promoted;
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

    public void clear() {
        replicatedUpdates.clear();
        promoted = false;
        active = true;
    }
}
