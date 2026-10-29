package com.drs.comm.model;


import java.util.ArrayList;
import java.util.List;

/**
 * Result object returned by collective communication services and REST endpoints.
 * Includes per-rank execution timeline, scattered and gathered arrays, self-check status,
 * and detailed diagnostic steps.
 */
public class CollectiveResult {

    private String operation;

    private int numProcesses;
    private int chunkSize;
    private int rootRank;
    private String alertText;
    private List<String> steps = new ArrayList<>();
    private List<RankExecutionRecord> rankResults = new ArrayList<>();
    private List<RankTimelineEntry> timeline = new ArrayList<>();
    private List<String> scattered = new ArrayList<>();
    private List<String> gathered = new ArrayList<>();

    // Numeric buffers for backwards compatibility
    private int[] alertData;
    private int[] scatterData;
    private int[] gatheredData;

    private boolean selfCheckPassed;

    private long durationMs;
    private String status = "SUCCESS";
    private String errorMessage;

    public CollectiveResult() {
    }

    public CollectiveResult(String operation, int numProcesses, int chunkSize, int rootRank) {
        this.operation = operation;
        this.numProcesses = numProcesses;
        this.chunkSize = chunkSize;
        this.rootRank = rootRank;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public int getProcesses() {
        return numProcesses;
    }

    public void setProcesses(int processes) {
        this.numProcesses = processes;
    }

    public int getNumProcesses() {
        return numProcesses;
    }

    public void setNumProcesses(int numProcesses) {
        this.numProcesses = numProcesses;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getRootRank() {
        return rootRank;
    }

    public void setRootRank(int rootRank) {
        this.rootRank = rootRank;
    }

    public String getAlertText() {
        return alertText;
    }

    public void setAlertText(String alertText) {
        this.alertText = alertText;
    }

    public List<String> getSteps() {
        return steps;
    }

    public void setSteps(List<String> steps) {
        this.steps = steps;
    }

    public void addStep(String step) {
        this.steps.add(step);
    }

    public List<RankExecutionRecord> getRankResults() {
        return rankResults;
    }

    public void setRankResults(List<RankExecutionRecord> rankResults) {
        this.rankResults = rankResults;
    }

    public List<RankTimelineEntry> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<RankTimelineEntry> timeline) {
        this.timeline = timeline;
    }

    public void addTimelineEntry(int rank, String action, Object data, int order) {
        this.timeline.add(new RankTimelineEntry(rank, action, data, order));
    }

    // Alias for getTimeline
    public List<RankTimelineEntry> getPerRankTimeline() {
        return timeline;
    }

    public List<String> getScattered() {
        return scattered;
    }

    public void setScattered(List<String> scattered) {
        this.scattered = scattered;
    }

    public List<String> getGathered() {
        return gathered;
    }

    public void setGathered(List<String> gathered) {
        this.gathered = gathered;
    }

    public int[] getAlertData() {
        return alertData;
    }

    public void setAlertData(int[] alertData) {
        this.alertData = alertData;
    }

    public int[] getScatterData() {
        return scatterData;
    }

    public void setScatterData(int[] scatterData) {
        this.scatterData = scatterData;
    }

    public int[] getGatheredData() {
        return gatheredData;
    }

    public void setGatheredData(int[] gatheredData) {
        this.gatheredData = gatheredData;
    }

    public boolean isSelfCheckPassed() {
        return selfCheckPassed;
    }

    public void setSelfCheckPassed(boolean selfCheckPassed) {
        this.selfCheckPassed = selfCheckPassed;
    }

    public boolean isVerified() {
        return selfCheckPassed;
    }

    public void setVerified(boolean verified) {
        this.selfCheckPassed = verified;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
