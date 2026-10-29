package com.drs.core.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity representing a registered UAV or edge node in the disaster zone.
 * <p>
 * Exp 1 — registration + connectivity scoring.<br>
 * Exp 2 — heartbeat reception on a fixed thread pool + silent-node detection.
 */
@Entity
@Table(name = "nodes")
public class NodeEntity {

    @Id
    @Column(name = "node_id", nullable = false)
    private String nodeId;

    private int battery;
    private int signal;
    private String nodeType;   // "UAV" or "EDGE"
    private String clusterId;

    /** Connectivity score = 2 × signal + battery. */
    private int connectivityScore;

    /** Derived state label: STABLE, AT_RISK, PARTITION_LIKELY, PARTITIONED */
    private String state;

    /** ALIVE or SILENT. */
    @Column(nullable = false)
    private String status = "ALIVE";

    /** Last heartbeat reception time. */
    private Instant lastHeartbeat;

    /** Name of the pool thread that handled the most recent heartbeat. */
    private String lastThreadName;

    private Instant registeredAt;

    public NodeEntity() { }

    public NodeEntity(String nodeId, int battery, int signal, String nodeType, String clusterId) {
        this.nodeId = nodeId;
        this.battery = battery;
        this.signal = signal;
        this.nodeType = nodeType;
        this.clusterId = clusterId;
        this.registeredAt = Instant.now();
        this.lastHeartbeat = this.registeredAt;
        computeScore();
    }

    /** Same formula as legacy ClusterCoordinatorImpl.handleRegister(). */
    public void computeScore() {
        this.connectivityScore = (2 * signal) + battery;
        if (connectivityScore >= 150) {
            this.state = "STABLE";
        } else if (connectivityScore >= 100) {
            this.state = "AT_RISK";
        } else if (connectivityScore >= 50) {
            this.state = "PARTITION_LIKELY";
        } else {
            this.state = "PARTITIONED";
        }
    }

    public void recalculateScore() {
        computeScore();
    }

    // ── getters ──

    public String getNodeId()            { return nodeId; }
    public int getBattery()              { return battery; }
    public int getSignal()               { return signal; }
    public String getNodeType()          { return nodeType; }
    public String getClusterId()         { return clusterId; }
    public int getConnectivityScore()    { return connectivityScore; }
    public String getState()             { return state != null ? state : "STABLE"; }
    public String getStatus()            { return status; }
    public Instant getLastHeartbeat()    { return lastHeartbeat; }
    public String getLastThreadName()    { return lastThreadName; }
    public Instant getRegisteredAt()     { return registeredAt; }

    // ── setters ──

    public void setNodeId(String v)               { this.nodeId = v; }
    public void setBattery(int v)                 { this.battery = v; computeScore(); }
    public void setSignal(int v)                  { this.signal = v; computeScore(); }
    public void setNodeType(String v)             { this.nodeType = v; }
    public void setClusterId(String v)            { this.clusterId = v; }
    public void setStatus(String v)               { this.status = v; }
    public void setLastHeartbeat(Instant v)        { this.lastHeartbeat = v; }
    public void setLastThreadName(String v)        { this.lastThreadName = v; }
    public void setConnectivityScore(int v)       { this.connectivityScore = v; }
    public void setState(String v)                { this.state = v; }
}
