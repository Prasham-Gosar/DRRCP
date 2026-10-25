package com.drs.faulttolerance.model;

import java.util.List;
import java.util.Map;

/**
 * Result of a fault-tolerance operation (process update, fail, promote, etc.).
 * Carries a structured step trace for the UI.
 */
public class FaultToleranceResult {

    private final String primaryId;
    private final String backupId;
    private final boolean primaryActive;
    private final boolean backupPromoted;
    private final Map<String, SystemUpdate> currentState;
    private final List<String> steps;

    public FaultToleranceResult(String primaryId, String backupId,
                                boolean primaryActive, boolean backupPromoted,
                                Map<String, SystemUpdate> currentState,
                                List<String> steps) {
        this.primaryId = primaryId;
        this.backupId = backupId;
        this.primaryActive = primaryActive;
        this.backupPromoted = backupPromoted;
        this.currentState = currentState;
        this.steps = steps;
    }

    public String getPrimaryId()                    { return primaryId; }
    public String getBackupId()                     { return backupId; }
    public boolean isPrimaryActive()                { return primaryActive; }
    public boolean isBackupPromoted()               { return backupPromoted; }
    public Map<String, SystemUpdate> getCurrentState() { return currentState; }
    public List<String> getSteps()                  { return steps; }
}
