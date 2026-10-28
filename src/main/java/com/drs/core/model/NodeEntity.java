package com.drs.core.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Node entity representing a disaster relief participant (Exp 1 & 2).
 */
@Entity
@Table(name = "nodes")
public class NodeEntity {

    @Id
    @Column(name = "node_id", nullable = false)
    private String nodeId;

    private int battery;
    private int signal;
    private String nodeType;
    private String clusterId;
    private String status = "ALIVE";
    private Instant lastHeartbeat;
    private String lastThreadName;
    private int connectivityScore;
    private String state;

    public NodeEntity() {
    }

    public NodeEntity(String nodeId, int battery, int signal, String nodeType, String clusterId) {
        this.nodeId = nodeId;
        this.battery = battery;
        this.signal = signal;
        this.nodeType = nodeType;
        this.clusterId = clusterId;
        this.lastHeartbeat = Instant.now();
        this.status = "ALIVE";
        recalculateScore();
    }

    public void recalculateScore() {
        this.connectivityScore = 2 * signal + battery;
        if (connectivityScore >= 150) {
            this.state = "STABLE";
        } else if (connectivityScore >= 80) {
            this.state = "AT_RISK";
        } else if (connectivityScore >= 40) {
            this.state = "PARTITION_LIKELY";
        } else {
            this.state = "PARTITIONED";
        }
    }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public int getBattery() { return battery; }
    public void setBattery(int battery) { this.battery = battery; recalculateScore(); }

    public int getSignal() { return signal; }
    public void setSignal(int signal) { this.signal = signal; recalculateScore(); }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getClusterId() { return clusterId; }
    public void setClusterId(String clusterId) { this.clusterId = clusterId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public String getLastThreadName() { return lastThreadName; }
    public void setLastThreadName(String lastThreadName) { this.lastThreadName = lastThreadName; }

    public int getConnectivityScore() { return connectivityScore; }
    public void setConnectivityScore(int connectivityScore) { this.connectivityScore = connectivityScore; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
}

