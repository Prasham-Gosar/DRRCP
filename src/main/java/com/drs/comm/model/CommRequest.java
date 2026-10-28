package com.drs.comm.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Request payload for collective communication operations.
 * Supports both modern disaster-flavoured parameters (processes, chunkSize, alertText, zoneIds)
 * and legacy numeric parameters (alertData, packagesData) for full backwards compatibility.
 */
public class CommRequest {

    @JsonProperty("processes")
    @JsonAlias({"numProcesses", "ranks"})
    private int processes = 4;

    private int chunkSize = 2;
    private int rootRank = 0;
    private String alertText = "CRITICAL: Sector 4 Flash Flood Warning - Immediate Evacuation & Relief Operation";
    private int[] alertData = new int[]{911, 12, 5};
    private List<String> zoneIds = new ArrayList<>();
    private int[] packagesData;

    public CommRequest() {
    }

    public CommRequest(int processes, int chunkSize, int rootRank) {
        this.processes = processes;
        this.chunkSize = chunkSize;
        this.rootRank = rootRank;
    }

    public int getProcesses() {
        return processes;
    }

    public void setProcesses(int processes) {
        this.processes = processes;
    }

    // Alias for backwards compatibility
    public int getNumProcesses() {
        return processes;
    }

    public void setNumProcesses(int numProcesses) {
        this.processes = numProcesses;
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

    public int[] getAlertData() {
        return alertData;
    }

    public void setAlertData(int[] alertData) {
        this.alertData = alertData;
    }

    public List<String> getZoneIds() {
        return zoneIds;
    }

    public void setZoneIds(List<String> zoneIds) {
        this.zoneIds = zoneIds;
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
                "processes=" + processes +
                ", chunkSize=" + chunkSize +
                ", rootRank=" + rootRank +
                ", alertText='" + alertText + '\'' +
                ", alertData=" + Arrays.toString(alertData) +
                ", zoneIds=" + zoneIds +
                ", packagesData=" + Arrays.toString(packagesData) +
                '}';
    }
}
