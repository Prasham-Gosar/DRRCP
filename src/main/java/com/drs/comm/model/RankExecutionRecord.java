package com.drs.comm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Execution record capturing what an individual simulated rank performed.
 */
public class RankExecutionRecord {

    private int rank;
    private boolean isRoot;
    private List<String> steps = new ArrayList<>();
    private int[] receivedAlert;
    private int[] receivedChunk;
    private int[] deliveredChunk;

    public RankExecutionRecord() {
    }

    public RankExecutionRecord(int rank, boolean isRoot) {
        this.rank = rank;
        this.isRoot = isRoot;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public boolean isRoot() {
        return isRoot;
    }

    public void setRoot(boolean root) {
        isRoot = root;
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

    public int[] getReceivedAlert() {
        return receivedAlert;
    }

    public void setReceivedAlert(int[] receivedAlert) {
        this.receivedAlert = receivedAlert;
    }

    public int[] getReceivedChunk() {
        return receivedChunk;
    }

    public void setReceivedChunk(int[] receivedChunk) {
        this.receivedChunk = receivedChunk;
    }

    public int[] getDeliveredChunk() {
        return deliveredChunk;
    }

    public void setDeliveredChunk(int[] deliveredChunk) {
        this.deliveredChunk = deliveredChunk;
    }
}
