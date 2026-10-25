package client;

import common.LoadNode;
import common.NodeStatus;
import common.RescueTask;
import java.util.ArrayList;
import java.util.List;
import loadbalancing.LoadBalancer;

public class LoadBalancingDemo {
    public static void main(String[] args) {
        System.out.println("   DISASTER RELIEF LOAD BALANCING SYSTEM");
        List<LoadNode> nodes = new ArrayList<>();
        LoadNode uav1 = new LoadNode(new NodeStatus("UAV-01", 80, 70, "UAV", "Cluster-A"), 10);
        LoadNode uav2 = new LoadNode(new NodeStatus("UAV-02", 60, 55, "UAV", "Cluster-A"), 10);
        LoadNode edge1 = new LoadNode(new NodeStatus("Edge-01", 90, 85, "EDGE", "Cluster-B"), 15);
        nodes.add(uav1);
        nodes.add(uav2);
        nodes.add(edge1);

        System.out.println();
        System.out.println(
                "========== INITIAL NODE STATUS ==========");
        for (LoadNode node : nodes) {
            System.out.println(node);
        }

        LoadBalancer loadBalancer = new LoadBalancer(nodes);
        List<RescueTask> tasks = List.of(
                new RescueTask("TASK-01", "Search for survivors", 3),
                new RescueTask("TASK-02", "Inspect damaged building", 2),
                new RescueTask("TASK-03", "Deliver medical supplies", 4),
                new RescueTask("TASK-04", "Monitor rescue location", 2),
                new RescueTask("TASK-05", "Relay communication", 3),
                new RescueTask("TASK-06", "Search nearby area", 2));
        System.out.println();
        System.out.println(
                "========== TASK ASSIGNMENT STARTED ==========");
        for (RescueTask task : tasks) {
            loadBalancer.assignTask(task);
        }
        loadBalancer.displayNodeStatus();

        System.out.println();
        System.out.println(
                "========== NODE FAILURE SIMULATION ==========");
        uav1.setAvailable(false);
        System.out.println(
                "UAV-01 is now unavailable.");

        RescueTask emergencyTask = new RescueTask("TASK-07", "Emergency survivor search", 2);
        System.out.println();
        System.out.println("Assigning a new task after UAV-01 failure...");
        loadBalancer.assignTask(emergencyTask);

        loadBalancer.displayNodeStatus();
    }
}