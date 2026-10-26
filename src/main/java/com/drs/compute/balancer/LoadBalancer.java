package com.drs.compute.balancer;

import com.drs.compute.model.AssignmentResult;
import com.drs.compute.model.LoadNode;
import com.drs.compute.model.NodeTrace;
import com.drs.compute.model.RescueTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Ported from legacy loadbalancing.LoadBalancer.
 * Assigns rescue tasks to candidate nodes by lowest projected utilization:
 *   projectedUtilization = (currentLoad + workload) / effectiveCapacity
 *
 * Algorithm behavior (strictly identical to legacy):
 * 1. Skips unavailable nodes (!node.isAvailable()).
 * 2. Skips nodes with non-positive effective capacity (effectiveCapacity <= 0).
 * 3. Evaluates projected utilization for remaining nodes.
 * 4. Selects the node with strictly lowest projected utilization (first evaluated node wins on ties).
 * 5. Increments chosen node's load and records execution steps and trace evaluations.
 */
public class LoadBalancer {

    private final List<LoadNode> nodes;

    public LoadBalancer(List<LoadNode> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
    }

    public List<LoadNode> getNodes() {
        return nodes;
    }

    /**
     * Assigns a rescue task to the optimal node according to effective capacity.
     *
     * @param task the rescue task to assign
     * @return structured AssignmentResult with chosen node, trace, and steps
     */
    public AssignmentResult assignTask(RescueTask task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }

        AssignmentResult result = new AssignmentResult(
                task.getTaskId(),
                task.getDescription(),
                task.getWorkload()
        );

        result.addStep("Assigning Task: " + task.getTaskId());
        result.addStep("Description: " + task.getDescription());
        result.addStep("Required Workload: " + task.getWorkload());

        LoadNode selectedNode = null;
        double lowestUtilization = Double.POSITIVE_INFINITY;

        for (LoadNode node : nodes) {
            double effectiveCap = node.getEffectiveCapacity();

            if (!node.isAvailable()) {
                String reason = node.getNodeId() + " is unavailable. Skipping.";
                result.addStep(reason);
                result.addEvaluation(new NodeTrace(
                        node.getNodeId(),
                        node.getCurrentLoad(),
                        null,
                        true,
                        reason,
                        effectiveCap
                ));
                continue;
            }

            if (effectiveCap <= 0) {
                String reason = node.getNodeId() + " has no effective capacity. Skipping.";
                result.addStep(reason);
                result.addEvaluation(new NodeTrace(
                        node.getNodeId(),
                        node.getCurrentLoad(),
                        null,
                        true,
                        reason,
                        effectiveCap
                ));
                continue;
            }

            double projectedLoad = node.getCurrentLoad() + task.getWorkload();
            double projectedUtilization = projectedLoad / effectiveCap;

            result.addStep(String.format("%s | Current Load = %.2f | Projected Utilization = %.2f",
                    node.getNodeId(), node.getCurrentLoad(), projectedUtilization));

            result.addEvaluation(new NodeTrace(
                    node.getNodeId(),
                    node.getCurrentLoad(),
                    projectedUtilization,
                    false,
                    null,
                    effectiveCap
            ));

            if (projectedUtilization < lowestUtilization) {
                lowestUtilization = projectedUtilization;
                selectedNode = node;
            }
        }

        if (selectedNode == null) {
            result.addStep("No available node found for this task.");
            result.setSuccess(false);
            return result;
        }

        selectedNode.addLoad(task.getWorkload());

        result.setSuccess(true);
        result.setChosenNodeId(selectedNode.getNodeId());
        result.setUpdatedLoad(selectedNode.getCurrentLoad());
        result.setEffectiveCapacity(selectedNode.getEffectiveCapacity());
        result.setFinalUtilization(selectedNode.getUtilization());

        result.addStep("TASK ASSIGNED SUCCESSFULLY");
        result.addStep("Task " + task.getTaskId() + " assigned to " + selectedNode.getNodeId());
        result.addStep("Updated Load = " + String.format("%.2f", selectedNode.getCurrentLoad()));

        return result;
    }
}
