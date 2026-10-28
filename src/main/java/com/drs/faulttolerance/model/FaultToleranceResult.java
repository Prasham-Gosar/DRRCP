package com.drs.faulttolerance.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of a fault-tolerance operation (process update, fail, promote, etc.).
 * Carries structured state maps for both replicas and a step trace for the UI.
 */
public class FaultToleranceResult {

    private final String primaryId;
    private final String backupId;
    private final boolean primaryActive;
    private final boolean backupPromoted;
    private final Map<String, SystemUpdate> primaryState;
    private final Map<String, SystemUpdate> backupState;
    private final Map<String, SystemUpdate> currentState;
    private final List<String> steps;

    public FaultToleranceResult(String primaryId, String backupId,
                                boolean primaryActive, boolean backupPromoted,
                                Map<String, SystemUpdate> currentState,
                                List<String> steps) {
        this(primaryId, backupId, primaryActive, backupPromoted, currentState, currentState, currentState, steps);
    }

    public FaultToleranceResult(String primaryId, String backupId,
                                boolean primaryActive, boolean backupPromoted,
                                Map<String, SystemUpdate> primaryState,
                                Map<String, SystemUpdate> backupState,
                                Map<String, SystemUpdate> currentState,
                                List<String> steps) {
        this.primaryId = primaryId;
        this.backupId = backupId;
        this.primaryActive = primaryActive;
        this.backupPromoted = backupPromoted;
        this.primaryState = primaryState != null ? primaryState : Collections.emptyMap();
        this.backupState = backupState != null ? backupState : Collections.emptyMap();
        this.currentState = currentState != null ? currentState : Collections.emptyMap();
        this.steps = steps;
    }

    public String getPrimaryId()                    { return primaryId; }
    public String getBackupId()                     { return backupId; }
    public boolean isPrimaryActive()                { return primaryActive; }
    public boolean isBackupPromoted()               { return backupPromoted; }
    public boolean isPromoted()                     { return backupPromoted; }
    public Map<String, SystemUpdate> getPrimaryState() { return primaryState; }
    public Map<String, SystemUpdate> getBackupState()  { return backupState; }
    public Map<String, SystemUpdate> getCurrentState() { return currentState; }
    public List<String> getSteps()                  { return steps; }
}
