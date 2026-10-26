package com.drs.compute.model;

import java.io.Serializable;

/**
 * Model representing a rescue task in the disaster relief system.
 * Ported from legacy common.RescueTask with additional metadata (clusterId, type).
 */
public class RescueTask implements Serializable {
    private static final long serialVersionUID = 1L;

    private String taskId;
    private String description;
    private double workload;
    private String clusterId;
    private String type;

    public RescueTask() {
    }

    public RescueTask(String taskId, String description, double workload) {
        this(taskId, description, workload, "Cluster-A", "General");
    }

    public RescueTask(String taskId, String description, double workload, String clusterId, String type) {
        this.taskId = taskId;
        this.description = description;
        this.workload = workload;
        this.clusterId = clusterId;
        this.type = type;
    }

    public static RescueTask fromEntity(RescueTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RescueTask(
                entity.getTaskId(),
                entity.getDescription(),
                entity.getWorkload(),
                entity.getClusterId(),
                entity.getType()
        );
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

    @Override
    public String toString() {
        return taskId + " | " + description + " | Workload=" + workload
                + (clusterId != null ? " | Cluster=" + clusterId : "")
                + (type != null ? " | Type=" + type : "");
    }
}
