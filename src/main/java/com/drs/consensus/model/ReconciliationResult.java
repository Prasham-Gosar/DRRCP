package com.drs.consensus.model;

import java.util.List;

/**
 * Result of running the reconciliation engine across all pending updates.
 * Contains the list of {@link Resolution}s plus a structured step trace for the UI.
 */
public class ReconciliationResult {

    private final List<Resolution> resolutions;
    private final List<String> steps;
    private final int totalUpdates;
    private final int conflictsDetected;
    private final int humanReviewRequired;

    public ReconciliationResult(List<Resolution> resolutions, List<String> steps,
                                int totalUpdates, int conflictsDetected, int humanReviewRequired) {
        this.resolutions = resolutions;
        this.steps = steps;
        this.totalUpdates = totalUpdates;
        this.conflictsDetected = conflictsDetected;
        this.humanReviewRequired = humanReviewRequired;
    }

    public List<Resolution> getResolutions()  { return resolutions; }
    public List<String> getSteps()            { return steps; }
    public int getTotalUpdates()              { return totalUpdates; }
    public int getConflictsDetected()         { return conflictsDetected; }
    public int getHumanReviewRequired()       { return humanReviewRequired; }
}
