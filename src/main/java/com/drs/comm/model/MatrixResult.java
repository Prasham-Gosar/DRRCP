package com.drs.comm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result object for Parallel Matrix Multiplication (Experiment 10).
 * Captures benchmark timings, speedup, efficiency, verification status, and sample matrix slice.
 */
public class MatrixResult {

    private int n;
    private int threads;
    private double sequentialMs;
    private double parallelMs;
    private double speedup;
    private double efficiency;
    private boolean correct;
    private int[][] sampleResult;
    private List<String> steps = new ArrayList<>();
    private String completedAt;

    public MatrixResult() {
    }

    public MatrixResult(int n, int threads) {
        this.n = n;
        this.threads = threads;
    }

    public int getN() {
        return n;
    }

    public void setN(int n) {
        this.n = n;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public double getSequentialMs() {
        return sequentialMs;
    }

    public void setSequentialMs(double sequentialMs) {
        this.sequentialMs = sequentialMs;
    }

    public double getParallelMs() {
        return parallelMs;
    }

    public void setParallelMs(double parallelMs) {
        this.parallelMs = parallelMs;
    }

    public double getSpeedup() {
        return speedup;
    }

    public void setSpeedup(double speedup) {
        this.speedup = speedup;
    }

    public double getEfficiency() {
        return efficiency;
    }

    public void setEfficiency(double efficiency) {
        this.efficiency = efficiency;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public int[][] getSampleResult() {
        return sampleResult;
    }

    public void setSampleResult(int[][] sampleResult) {
        this.sampleResult = sampleResult;
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

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public String toString() {
        return "MatrixResult{" +
                "n=" + n +
                ", threads=" + threads +
                ", sequentialMs=" + sequentialMs +
                ", parallelMs=" + parallelMs +
                ", speedup=" + speedup +
                ", efficiency=" + efficiency +
                ", correct=" + correct +
                ", completedAt='" + completedAt + '\'' +
                '}';
    }
}
