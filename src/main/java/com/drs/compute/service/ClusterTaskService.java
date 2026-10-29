package com.drs.compute.service;

import com.drs.compute.balancer.LoadBalancer;
import com.drs.compute.model.AssignmentResult;
import com.drs.compute.model.LoadNode;
import com.drs.compute.model.RescueTask;
import com.drs.compute.model.RescueTaskEntity;
import com.drs.compute.repository.RescueTaskRepository;
import com.drs.consensus.LeaderProvider;
import com.drs.core.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service managing cluster-partitioned task generation and leader-directed allocation (Day 4 Pipeline).
 * <p>
 * During a network partition between disaster clusters (e.g. Cluster-A and Cluster-B):
 * <ol>
 *   <li>Tasks arrive locally within each partitioned sector.</li>
 *   <li>Each cluster's locally elected leader (queried via {@link LeaderProvider#currentLeader(String)})
 *       acts as the local dispatcher.</li>
 *   <li>The leader assigns tasks strictly to candidate nodes within its own cluster partition using
 *       effective-capacity load balancing.</li>
 *   <li>Events are published displaying which leader assigned which task.</li>
 *   <li>Once connectivity heals, full-dataset MapReduce analytics can be executed across all clusters.</li>
 * </ol>
 */
@Service
public class ClusterTaskService {

    private static final Logger log = LoggerFactory.getLogger(ClusterTaskService.class);

    private final RescueTaskRepository taskRepository;
    private final LoadBalancerService loadBalancerService;
    private final LeaderProvider leaderProvider;
    private final EventPublisher eventPublisher;

    @Autowired
    public ClusterTaskService(
            RescueTaskRepository taskRepository,
            LoadBalancerService loadBalancerService,
            @Autowired(required = false) @Qualifier("electionService") LeaderProvider leaderProvider,
            @Autowired(required = false) EventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.loadBalancerService = loadBalancerService;
        this.leaderProvider = leaderProvider;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Scenario pipeline method called by Prasham's ScenarioRunner during Phase 5 (Partitioned Updates).
     * <p>
     * Generates partitioned rescue tasks for both Cluster-A and Cluster-B, queries each cluster's
     * elected leader via Grasit's {@link LeaderProvider}, and balances tasks only among nodes in each
     * leader's respective cluster.
     *
     * @return list of {@link AssignmentResult} records for all dispatched tasks
     */
    public synchronized List<AssignmentResult> generateAndAssignPartitionedTasks() {
        log.info(">>> [TASK PIPELINE] Generating and assigning partitioned rescue tasks...");

        List<AssignmentResult> allResults = new ArrayList<>();

        // ── 1. Define Partitioned Tasks for Cluster-A and Cluster-B ──
        List<RescueTaskEntity> clusterATasks = List.of(
                new RescueTaskEntity("TASK-PART-A1", "Cluster-A: Aerial thermal scan for trapped survivors in Sector 1", 3.0, "Cluster-A", "Search", "PENDING", null),
                new RescueTaskEntity("TASK-PART-A2", "Cluster-A: Emergency trauma antibiotic and bandage air-drop", 4.0, "Cluster-A", "Medical", "PENDING", null),
                new RescueTaskEntity("TASK-PART-A3", "Cluster-A: High-calorie ration pack delivery to shelter", 2.0, "Cluster-A", "Food", "PENDING", null)
        );

        List<RescueTaskEntity> clusterBTasks = List.of(
                new RescueTaskEntity("TASK-PART-B1", "Cluster-B: Ground sonar search in collapsed municipal building", 5.0, "Cluster-B", "Search", "PENDING", null),
                new RescueTaskEntity("TASK-PART-B2", "Cluster-B: Field clinic mobile saline and triage replenishment", 3.0, "Cluster-B", "Medical", "PENDING", null),
                new RescueTaskEntity("TASK-PART-B3", "Cluster-B: Portable water purification unit transport", 2.0, "Cluster-B", "Food", "PENDING", null)
        );

        // ── 2. Assign Cluster-A Tasks via Cluster-A Elected Leader ──
        allResults.addAll(assignTasksForCluster("Cluster-A", clusterATasks, "UAV-02"));

        // ── 3. Assign Cluster-B Tasks via Cluster-B Elected Leader ──
        allResults.addAll(assignTasksForCluster("Cluster-B", clusterBTasks, "Edge-01"));

        log.info("<<< [TASK PIPELINE] Partitioned task allocation completed: {} tasks dispatched across clusters.",
                allResults.size());

        return allResults;
    }

    /**
     * Assigns a list of rescue tasks strictly within the target cluster, directed by the cluster leader.
     *
     * @param clusterId     target disaster cluster (e.g. "Cluster-A", "Cluster-B")
     * @param tasks         tasks to assign
     * @param defaultLeader fallback leader node ID if LeaderProvider returns null
     * @return assignment results for the cluster
     */
    public synchronized List<AssignmentResult> assignTasksForCluster(String clusterId,
                                                                    List<RescueTaskEntity> tasks,
                                                                    String defaultLeader) {
        // Resolve cluster leader
        String leaderId = resolveLeader(clusterId, defaultLeader);
        log.info("[PIPELINE] Cluster {} leader resolved to: {}", clusterId, leaderId);

        // Filter candidate nodes strictly belonging to this cluster
        List<LoadNode> clusterNodes = getClusterNodes(clusterId);
        log.info("[PIPELINE] Found {} candidate node(s) in {}: {}",
                clusterNodes.size(), clusterId,
                clusterNodes.stream().map(LoadNode::getNodeId).collect(Collectors.toList()));

        List<AssignmentResult> results = new ArrayList<>();

        for (RescueTaskEntity entity : tasks) {
            // Save entity initially in PENDING status
            entity.setStatus("PENDING");
            entity.setCreatedAt(Instant.now());
            entity.setAssignedByLeaderId(leaderId);
            entity = taskRepository.save(entity);

            RescueTask task = RescueTask.fromEntity(entity);
            LoadBalancer balancer = new LoadBalancer(clusterNodes);
            AssignmentResult result = balancer.assignTask(task);

            result.setClusterId(clusterId);
            result.setAssignedByLeaderId(leaderId);

            // Add pipeline step context
            result.getSteps().add(0, String.format("[PIPELINE ROUTE] Task %s routed to %s elected leader [%s]",
                    entity.getTaskId(), clusterId, leaderId));

            if (result.isSuccess()) {
                entity.setStatus("ASSIGNED");
                entity.setAssignedNodeId(result.getChosenNodeId());
                entity.setAssignedAt(Instant.now());
                entity.setAssignedByLeaderId(leaderId);
                taskRepository.save(entity);

                result.getSteps().add(String.format("[PIPELINE COMPLETE] Leader [%s] assigned task %s to node [%s] (final util: %.1f%%)",
                        leaderId, entity.getTaskId(), result.getChosenNodeId(),
                        (result.getFinalUtilization() != null ? result.getFinalUtilization() * 100 : 0)));

                log.info("[PIPELINE] Leader {} assigned {} to node {}",
                        leaderId, entity.getTaskId(), result.getChosenNodeId());
            } else {
                result.getSteps().add(String.format("[PIPELINE FAILED] Leader [%s] could not find available capacity in %s",
                        leaderId, clusterId));
                log.warn("[PIPELINE] Leader {} could not assign task {}", leaderId, entity.getTaskId());
            }

            // Publish TASK_ASSIGNED SSE event with leader attribution
            publishLeaderAssignmentEvent(result, clusterId, leaderId);

            results.add(result);
        }

        return results;
    }

    /**
     * Resolves the current leader using Grasit's {@link LeaderProvider}, falling back to default.
     */
    public String resolveLeader(String clusterId, String defaultLeader) {
        if (leaderProvider != null) {
            try {
                String elected = leaderProvider.currentLeader(clusterId);
                if (elected != null && !elected.isBlank() && !"NONE".equalsIgnoreCase(elected)) {
                    return elected;
                }
            } catch (Exception ex) {
                log.warn("Failed to query LeaderProvider for {}: {}", clusterId, ex.getMessage());
            }
        }
        return (defaultLeader != null && !defaultLeader.isBlank()) ? defaultLeader : "UAV-01";
    }

    /**
     * Retrieves or initialises nodes strictly belonging to the specified cluster.
     */
    private List<LoadNode> getClusterNodes(String clusterId) {
        List<LoadNode> allNodes = loadBalancerService.getCandidateNodes();
        List<LoadNode> filtered = allNodes.stream()
                .filter(n -> clusterId.equalsIgnoreCase(n.getClusterId()))
                .collect(Collectors.toList());

        if (!filtered.isEmpty()) {
            return filtered;
        }

        // Fallback default fleet if no nodes were pre-registered for this cluster
        if ("Cluster-B".equalsIgnoreCase(clusterId)) {
            LoadNode e1 = new LoadNode("Edge-01", 95, 90, 15.0);
            e1.setClusterId("Cluster-B");
            e1.setNodeType("EDGE");
            LoadNode e2 = new LoadNode("Edge-02", 70, 65, 15.0);
            e2.setClusterId("Cluster-B");
            e2.setNodeType("EDGE");
            return List.of(e1, e2);
        } else {
            LoadNode u1 = new LoadNode("UAV-01", 85, 75, 10.0);
            u1.setClusterId("Cluster-A");
            u1.setNodeType("UAV");
            LoadNode u2 = new LoadNode("UAV-02", 60, 50, 10.0);
            u2.setClusterId("Cluster-A");
            u2.setNodeType("UAV");
            return List.of(u1, u2);
        }
    }

    /**
     * Publishes an SSE event on the compute module detailing leader assignment.
     */
    private void publishLeaderAssignmentEvent(AssignmentResult result, String clusterId, String leaderId) {
        if (eventPublisher == null) return;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", result.getTaskId());
        payload.put("taskDescription", result.getTaskDescription());
        payload.put("workload", result.getWorkload());
        payload.put("clusterId", clusterId);
        payload.put("assignedNodeId", result.getChosenNodeId());
        payload.put("assignedByLeaderId", leaderId);
        payload.put("leaderId", leaderId);
        payload.put("success", result.isSuccess());
        payload.put("updatedLoad", result.getUpdatedLoad());
        payload.put("finalUtilization", result.getFinalUtilization());
        payload.put("timestamp", Instant.now().toString());
        payload.put("steps", result.getSteps());

        eventPublisher.publish("compute", "TASK_ASSIGNED", payload);
    }

    /**
     * Provides aggregated statistics for the partitioned task pipeline.
     */
    public Map<String, Object> getPipelineSummary() {
        List<RescueTaskEntity> all = taskRepository.findAll();
        long totalTasks = all.size();
        long assignedTasks = all.stream().filter(t -> "ASSIGNED".equalsIgnoreCase(t.getStatus())).count();
        double totalWorkload = all.stream().mapToDouble(RescueTaskEntity::getWorkload).sum();

        Map<String, Long> tasksPerCluster = all.stream()
                .collect(Collectors.groupingBy(RescueTaskEntity::getClusterId, Collectors.counting()));

        Map<String, Double> workloadPerCluster = all.stream()
                .collect(Collectors.groupingBy(RescueTaskEntity::getClusterId, Collectors.summingDouble(RescueTaskEntity::getWorkload)));

        Map<String, String> leaders = new LinkedHashMap<>();
        leaders.put("Cluster-A", resolveLeader("Cluster-A", "UAV-02"));
        leaders.put("Cluster-B", resolveLeader("Cluster-B", "Edge-01"));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalTasks", totalTasks);
        summary.put("assignedTasks", assignedTasks);
        summary.put("pendingTasks", totalTasks - assignedTasks);
        summary.put("totalWorkload", totalWorkload);
        summary.put("tasksPerCluster", tasksPerCluster);
        summary.put("workloadPerCluster", workloadPerCluster);
        summary.put("clusterLeaders", leaders);

        return summary;
    }
}
