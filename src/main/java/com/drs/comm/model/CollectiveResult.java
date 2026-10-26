package com.drs.comm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result object returned by collective communication services and REST endpoints.
 */
public class CollectiveResult {

    private String operation;
    private int numProcesses;
    private int chunkSize;
    private int rootRank;
    private List<String> steps = new ArrayList<>();
    private List<RankExecutionRecord> rankResults = new ArrayList<>();
    private int[] alertData;
    private int[] scatterData;
    private int[] gatheredData;
    private boolean verified;
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

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
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
