package com.drs.compute.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/**
 * JPA entity representing a rescue task stored in H2 database.
 */
@Entity
@Table(name = "rescue_tasks")
public class RescueTaskEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String taskId;

    private String description;
    private double workload;
    private String clusterId;
    private String type;             // e.g. "Medical", "Food", "Search"
    private String status;           // "PENDING", "ASSIGNED"
    private String assignedNodeId;
    private String assignedByLeaderId; // Node ID of the cluster leader that performed the assignment
    private Instant createdAt;
    private Instant assignedAt;

    protected RescueTaskEntity() {
    }

    public RescueTaskEntity(String taskId, String description, double workload,
                            String clusterId, String type, String status, String assignedNodeId) {
        this.taskId = taskId;
        this.description = description;
        this.workload = workload;
        this.clusterId = clusterId;
        this.type = type;
        this.status = status != null ? status : "PENDING";
        this.assignedNodeId = assignedNodeId;
        this.createdAt = Instant.now();
        if ("ASSIGNED".equalsIgnoreCase(status) && assignedNodeId != null) {
            this.assignedAt = Instant.now();
        }
    }

    public RescueTaskEntity(String taskId, String description, double workload,
                            String clusterId, String type, String status,
                            String assignedNodeId, String assignedByLeaderId) {
        this(taskId, description, workload, clusterId, type, status, assignedNodeId);
        this.assignedByLeaderId = assignedByLeaderId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getWorkload() {
        return workload;
    }

    public void setWorkload(double workload) {
        this.workload = workload;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        if ("ASSIGNED".equalsIgnoreCase(status) && this.assignedAt == null) {
            this.assignedAt = Instant.now();
        }
    }

    public String getAssignedNodeId() {
        return assignedNodeId;
    }

    public void setAssignedNodeId(String assignedNodeId) {
        this.assignedNodeId = assignedNodeId;
    }

    public String getAssignedByLeaderId() {
        return assignedByLeaderId;
    }

    public void setAssignedByLeaderId(String assignedByLeaderId) {
        this.assignedByLeaderId = assignedByLeaderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }
}
