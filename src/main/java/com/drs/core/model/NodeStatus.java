package com.drs.core.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * Ported from legacy common.NodeStatus — now a JPA entity persisted to H2.
 * Represents a UAV or edge node in the disaster zone.
 */
@Entity
@Table(name = "node_status")
public class NodeStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String nodeId;

    private int batteryLevel;
    private int signalStrength;
    private String nodeType;        // "UAV" or "EDGE"
    private String clusterId;

    /** Connectivity score = 2*signal + battery. Computed on registration. */
    private int connectivityScore;

    /** STABLE / AT_RISK / PARTITION_LIKELY / PARTITIONED */
    private String state;

    private Instant lastHeartbeat;
    private Instant registeredAt;

    protected NodeStatus() { }

    public NodeStatus(String nodeId, int batteryLevel, int signalStrength,
                      String nodeType, String clusterId) {
        this.nodeId = nodeId;
        this.batteryLevel = batteryLevel;
        this.signalStrength = signalStrength;
        this.nodeType = nodeType;
        this.clusterId = clusterId;
        this.registeredAt = Instant.now();
        this.lastHeartbeat = this.registeredAt;
        computeScore();
    }

    /** Same formula as legacy ClusterCoordinatorImpl.handleRegister(). */
    public void computeScore() {
        this.connectivityScore = (2 * signalStrength) + batteryLevel;
        if (connectivityScore >= 150) state = "STABLE";
        else if (connectivityScore >= 100) state = "AT_RISK";
        else if (connectivityScore >= 50) state = "PARTITION_LIKELY";
        else state = "PARTITIONED";
    }

    // ── getters & setters ──

    public String getNodeId()           { return nodeId; }
    public int getBatteryLevel()        { return batteryLevel; }
    public int getSignalStrength()      { return signalStrength; }
    public String getNodeType()         { return nodeType; }
    public String getClusterId()        { return clusterId; }
    public int getConnectivityScore()   { return connectivityScore; }
    public String getState()            { return state; }
    public Instant getLastHeartbeat()   { return lastHeartbeat; }
    public Instant getRegisteredAt()    { return registeredAt; }

    public void setLastHeartbeat(Instant ts) { this.lastHeartbeat = ts; }
    public void setState(String state)       { this.state = state; }
    public void setNodeType(String v)        { this.nodeType = v; }
    public void setClusterId(String v)       { this.clusterId = v; }
    public void setBatteryLevel(int v)       { this.batteryLevel = v; computeScore(); }
    public void setSignalStrength(int v)     { this.signalStrength = v; computeScore(); }
}
