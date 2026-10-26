package com.drs.compute.model;

import java.io.Serializable;

/**
 * Per-node evaluation trace recording candidate node metrics during task assignment.
 */
public class NodeTrace implements Serializable {
    private static final long serialVersionUID = 1L;

    private String nodeId;
    private double currentLoad;
    private Double projectedUtilization;
    private boolean skipped;
    private String skippedReason;
    private double effectiveCapacity;

    public NodeTrace() {
    }

    public NodeTrace(String nodeId, double currentLoad, Double projectedUtilization,
                     boolean skipped, String skippedReason, double effectiveCapacity) {
        this.nodeId = nodeId;
        this.currentLoad = currentLoad;
        this.projectedUtilization = projectedUtilization;
        this.skipped = skipped;
        this.skippedReason = skippedReason;
        this.effectiveCapacity = effectiveCapacity;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public double getCurrentLoad() {
        return currentLoad;
    }

    public void setCurrentLoad(double currentLoad) {
        this.currentLoad = currentLoad;
    }

    public Double getProjectedUtilization() {
        return projectedUtilization;
    }

    public void setProjectedUtilization(Double projectedUtilization) {
        this.projectedUtilization = projectedUtilization;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public String getSkippedReason() {
        return skippedReason;
    }

    public void setSkippedReason(String skippedReason) {
        this.skippedReason = skippedReason;
    }

    public double getEffectiveCapacity() {
        return effectiveCapacity;
    }

    public void setEffectiveCapacity(double effectiveCapacity) {
        this.effectiveCapacity = effectiveCapacity;
    }
}
