package com.drs.comm.model;

import java.util.Arrays;

/**
 * Request payload for collective communication operations.
 */
public class CommRequest {

    private int numProcesses = 4;
    private int chunkSize = 2;
    private int rootRank = 0;
    private int[] alertData = new int[]{911, 12, 5};
    private int[] packagesData;

    public CommRequest() {
    }

    public CommRequest(int numProcesses, int chunkSize, int rootRank) {
        this.numProcesses = numProcesses;
        this.chunkSize = chunkSize;
        this.rootRank = rootRank;
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

    public int[] getAlertData() {
        return alertData;
    }

    public void setAlertData(int[] alertData) {
        this.alertData = alertData;
    }

    public int[] getPackagesData() {
        return packagesData;
    }

    public void setPackagesData(int[] packagesData) {
        this.packagesData = packagesData;
    }

    @Override
    public String toString() {
        return "CommRequest{" +
                "numProcesses=" + numProcesses +
                ", chunkSize=" + chunkSize +
                ", rootRank=" + rootRank +
                ", alertData=" + Arrays.toString(alertData) +
                ", packagesData=" + Arrays.toString(packagesData) +
                '}';
    }
}
