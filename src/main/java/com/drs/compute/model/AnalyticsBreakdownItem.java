package com.drs.compute.model;

import java.io.Serializable;

/**
 * Fine-grained MapReduce aggregation bucket representing a composite key:
 * (Cluster, TaskType) -> (TaskCount, TotalWorkload).
 */
public class AnalyticsBreakdownItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private String cluster;
    private String taskType;
    private int taskCount;
    private double totalWorkload;

    public AnalyticsBreakdownItem() {
    }

    public AnalyticsBreakdownItem(String cluster, String taskType, int taskCount, double totalWorkload) {
        this.cluster = cluster;
        this.taskType = taskType;
        this.taskCount = taskCount;
        this.totalWorkload = totalWorkload;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public void setTaskCount(int taskCount) {
        this.taskCount = taskCount;
    }

    public double getTotalWorkload() {
        return totalWorkload;
    }

    public void setTotalWorkload(double totalWorkload) {
        this.totalWorkload = totalWorkload;
    }
}

