package loadbalancing;

import common.LoadNode;
import common.RescueTask;
import java.util.List;

public class LoadBalancer {
    private final List<LoadNode> nodes;

    public LoadBalancer(List<LoadNode> nodes) {
        this.nodes = nodes;
    }

    public LoadNode assignTask(RescueTask task) {
        LoadNode selectedNode = null;
        double lowestUtilization = Double.POSITIVE_INFINITY;
        System.out.println();
        System.out.println("Assigning Task: " + task.getTaskId());
        System.out.println("Description: " + task.getDescription());
        System.out.println("Required Workload: " + task.getWorkload());

        for (LoadNode node : nodes) {
            if (!node.isAvailable()) {
                System.out.println(node.getNodeId() + " is unavailable. Skipping.");
                continue;
            }

            double effectiveCapacity = node.getEffectiveCapacity();
            if (effectiveCapacity <= 0) {
                System.out.println(node.getNodeId() + " has no effective capacity. Skipping.");
                continue;
            }

            double projectedLoad = node.getCurrentLoad() + task.getWorkload();
            double projectedUtilization = projectedLoad / effectiveCapacity;
            System.out.println(
                    node.getNodeId() + " | Current Load = " + node.getCurrentLoad() + " | Projected Utilization = "
                            + String.format("%.2f", projectedUtilization));

            if (projectedUtilization < lowestUtilization) {
                lowestUtilization = projectedUtilization;
                selectedNode = node;
            }
        }

        if (selectedNode == null) {
            System.out.println("No available node found for this task.");
            return null;
        }

        selectedNode.addLoad(task.getWorkload());
        System.out.println();
        System.out.println("TASK ASSIGNED SUCCESSFULLY");
        System.out.println("Task " + task.getTaskId() + " assigned to " + selectedNode.getNodeId());
        System.out.println("Updated Load = " + selectedNode.getCurrentLoad());
        return selectedNode;
    }

    public void displayNodeStatus() {
        System.out.println();
        System.out.println(
                "========== CURRENT NODE STATUS ==========");
        for (LoadNode node : nodes) {
            System.out.println(
                    node.getNodeId() + " | Current Load = " + node.getCurrentLoad() + " | Effective Capacity = "
                            + String.format("%.2f", node.getEffectiveCapacity()) + " | Utilization = "
                            + String.format("%.2f", node.getUtilization()) + " | Status = "
                            + (node.isAvailable() ? "AVAILABLE" : "UNAVAILABLE"));
        }

        System.out.println("==========================================");
    }
}