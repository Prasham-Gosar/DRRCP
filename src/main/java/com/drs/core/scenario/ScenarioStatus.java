package com.drs.core.scenario;

import com.drs.consensus.model.ReconciliationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete snapshot of the Scenario Runner pipeline state.
 */
public class ScenarioStatus {

    private boolean running = false;
    private String state = "IDLE"; // IDLE, RUNNING, COMPLETED, FAILED
    private int currentPhase = 0;
    private String currentPhaseName = "NOT_STARTED";
    private long startedAt;
    private long completedAt;
    private long totalDurationMs;
    private String errorMessage;
    private List<PhaseDetail> phases = new ArrayList<>();
    private ReconciliationResult reconciliation;
    private List<String> logs = new ArrayList<>();

    public ScenarioStatus() { }

    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public int getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(int currentPhase) { this.currentPhase = currentPhase; }

    public String getCurrentPhaseName() { return currentPhaseName; }
    public void setCurrentPhaseName(String currentPhaseName) { this.currentPhaseName = currentPhaseName; }

    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }

    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }

    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public List<PhaseDetail> getPhases() { return phases; }
    public void setPhases(List<PhaseDetail> phases) { this.phases = phases; }

    public ReconciliationResult getReconciliation() { return reconciliation; }
    public void setReconciliation(ReconciliationResult reconciliation) { this.reconciliation = reconciliation; }

    public List<String> getLogs() { return logs; }
    public void setLogs(List<String> logs) { this.logs = logs; }
}

