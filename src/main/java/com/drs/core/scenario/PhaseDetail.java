package com.drs.core.scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * Detailed telemetry and step execution trace for a single phase of the scenario runner.
 */
public class PhaseDetail {

    private int phaseNumber;
    private String name;
    private String title;
    private String status = "PENDING"; // PENDING, RUNNING, COMPLETED, FAILED
    private long startedAt;
    private long finishedAt;
    private long durationMs;
    private String summary = "";
    private List<String> steps = new ArrayList<>();

    public PhaseDetail() { }

    public PhaseDetail(int phaseNumber, String name, String title) {
        this.phaseNumber = phaseNumber;
        this.name = name;
        this.title = title;
    }

    public int getPhaseNumber() { return phaseNumber; }
    public void setPhaseNumber(int phaseNumber) { this.phaseNumber = phaseNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }

    public long getFinishedAt() { return finishedAt; }
    public void setFinishedAt(long finishedAt) { this.finishedAt = finishedAt; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = steps; }

    public void addStep(String step) {
        this.steps.add(step);
    }
}

