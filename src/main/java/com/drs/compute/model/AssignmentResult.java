package com.drs.compute.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result object returned by the load balancer.
 * Contains the assigned node, updated loads, per-node trace list, and execution steps.
 */
public class AssignmentResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String taskId;
    private String taskDescription;
    private double workload;
    private boolean success;
    private String chosenNodeId;
    private String clusterId;
    private String assignedByLeaderId;
    private Double updatedLoad;
    private Double effectiveCapacity;
    private Double finalUtilization;
    private List<NodeTrace> evaluations = new ArrayList<>();
    private List<String> steps = new ArrayList<>();

    public AssignmentResult() {
    }

    public AssignmentResult(String taskId, String taskDescription, double workload) {
        this.taskId = taskId;
        this.taskDescription = taskDescription;
        this.workload = workload;
        this.success = false;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    public double getWorkload() {
        return workload;
    }

    public void setWorkload(double workload) {
        this.workload = workload;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getChosenNodeId() {
        return chosenNodeId;
    }

    public void setChosenNodeId(String chosenNodeId) {
        this.chosenNodeId = chosenNodeId;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getAssignedByLeaderId() {
        return assignedByLeaderId;
    }

    public void setAssignedByLeaderId(String assignedByLeaderId) {
        this.assignedByLeaderId = assignedByLeaderId;
    }

    public Double getUpdatedLoad() {
        return updatedLoad;
    }

    public void setUpdatedLoad(Double updatedLoad) {
        this.updatedLoad = updatedLoad;
    }

    public Double getEffectiveCapacity() {
        return effectiveCapacity;
    }

    public void setEffectiveCapacity(Double effectiveCapacity) {
        this.effectiveCapacity = effectiveCapacity;
    }

    public Double getFinalUtilization() {
        return finalUtilization;
    }

    public void setFinalUtilization(Double finalUtilization) {
        this.finalUtilization = finalUtilization;
    }

    public List<NodeTrace> getEvaluations() {
        return evaluations;
    }

    public void setEvaluations(List<NodeTrace> evaluations) {
        this.evaluations = evaluations != null ? evaluations : new ArrayList<>();
    }

    public void addEvaluation(NodeTrace trace) {
        this.evaluations.add(trace);
    }

    public List<String> getSteps() {
        return steps;
    }

    public void setSteps(List<String> steps) {
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    public void addStep(String step) {
        this.steps.add(step);
    }
}
