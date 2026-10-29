package com.drs.comm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result object for multi-threaded benchmark sweeps across thread counts (1, 2, 4, 8...).
 */
public class MatrixSweepResult {

    private int n;
    private int maxThreads;
    private List<MatrixResult> runs = new ArrayList<>();
    private int bestThreads;
    private double bestSpeedup;
    private String completedAt;

    public MatrixSweepResult() {
    }

    public MatrixSweepResult(int n, int maxThreads) {
        this.n = n;
        this.maxThreads = maxThreads;
    }

    public int getN() {
        return n;
    }

    public void setN(int n) {
        this.n = n;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public List<MatrixResult> getRuns() {
        return runs;
    }

    public void setRuns(List<MatrixResult> runs) {
        this.runs = runs;
    }

    public void addRun(MatrixResult run) {
        this.runs.add(run);
    }

    public int getBestThreads() {
        return bestThreads;
    }

    public void setBestThreads(int bestThreads) {
        this.bestThreads = bestThreads;
    }

    public double getBestSpeedup() {
        return bestSpeedup;
    }

    public void setBestSpeedup(double bestSpeedup) {
        this.bestSpeedup = bestSpeedup;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public String toString() {
        return "MatrixSweepResult{" +
                "n=" + n +
                ", maxThreads=" + maxThreads +
                ", runsCount=" + runs.size() +
                ", bestThreads=" + bestThreads +
                ", bestSpeedup=" + bestSpeedup +
                '}';
    }
}
