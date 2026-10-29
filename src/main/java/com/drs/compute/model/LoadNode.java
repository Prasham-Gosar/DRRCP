package com.drs.compute.model;

import com.drs.core.model.NodeStatus;
import java.io.Serializable;

/**
 * Ported from legacy common.LoadNode.
 * Represents a UAV or edge node candidate for load balancing.
 * Effective capacity is dynamically adjusted based on battery level and signal strength:
 * effectiveCapacity = maxCapacity * ((batteryLevel/100 + signalStrength/100) / 2)
 */
public class LoadNode implements Serializable {
    private static final long serialVersionUID = 1L;

    private String nodeId;
    private int batteryLevel;
    private int signalStrength;
    private double maxCapacity;
    private double currentLoad;
    private boolean available;
    private String clusterId;
    private String nodeType;

    public LoadNode() {
        this.available = true;
    }

    public LoadNode(String nodeId, int batteryLevel, int signalStrength, double maxCapacity) {
        this.nodeId = nodeId;
        this.batteryLevel = batteryLevel;
        this.signalStrength = signalStrength;
        this.maxCapacity = maxCapacity;
        this.currentLoad = 0.0;
        this.available = true;
        this.clusterId = "Cluster-A";
        this.nodeType = "UAV";
    }

    public LoadNode(NodeStatus status, double maxCapacity) {
        this.nodeId = status.getNodeId();
        this.batteryLevel = status.getBatteryLevel();
        this.signalStrength = status.getSignalStrength();
        this.clusterId = status.getClusterId();
        this.nodeType = status.getNodeType();
        this.maxCapacity = maxCapacity;
        this.currentLoad = 0.0;
        this.available = true;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public int getSignalStrength() {
        return signalStrength;
    }

    public void setSignalStrength(int signalStrength) {
        this.signalStrength = signalStrength;
    }

    public double getMaxCapacity() {
        return maxCapacity;
    }

    public double getCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(double maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public double getCurrentLoad() {
        return currentLoad;
    }

    public double getLoad() {
        return currentLoad;
    }

    public void setCurrentLoad(double currentLoad) {
        this.currentLoad = currentLoad;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getStatus() {
        return available ? "AVAILABLE" : "UNAVAILABLE";
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public void addLoad(double workload) {
        this.currentLoad += workload;
    }

    /**
     * Effective capacity = maxCapacity * (batteryFactor + signalFactor) / 2.0
     * Preserves exact formula from legacy common.LoadNode.
     */
    public double getEffectiveCapacity() {
        double batteryFactor = batteryLevel / 100.0;
        double signalFactor = signalStrength / 100.0;
        double capacityFactor = (batteryFactor + signalFactor) / 2.0;
        return maxCapacity * capacityFactor;
    }

    /**
     * Current utilization = currentLoad / effectiveCapacity.
     * Returns POSITIVE_INFINITY if effective capacity <= 0.
     */
    public double getUtilization() {
        double capacity = getEffectiveCapacity();
        if (capacity <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return currentLoad / capacity;
    }

    @Override
    public String toString() {
        return nodeId
                + " | Battery=" + batteryLevel
                + "% | Signal=" + signalStrength
                + "% | Load=" + currentLoad
                + " | Effective Capacity="
                + String.format("%.2f", getEffectiveCapacity());
    }
}
