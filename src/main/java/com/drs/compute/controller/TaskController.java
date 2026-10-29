package com.drs.compute.controller;

import com.drs.compute.model.AssignmentResult;
import com.drs.compute.model.RescueTaskEntity;
import com.drs.compute.service.ClusterTaskService;
import com.drs.compute.service.LoadBalancerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Rescue Task operations, single-task assignment (Exp 6),
 * and cluster-partitioned leader pipeline orchestration (Day 4).
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final LoadBalancerService loadBalancerService;
    private final ClusterTaskService clusterTaskService;

    @Autowired
    public TaskController(LoadBalancerService loadBalancerService,
                          ClusterTaskService clusterTaskService) {
        this.loadBalancerService = loadBalancerService;
        this.clusterTaskService = clusterTaskService;
    }

    public record CreateTaskRequest(
            String taskId,
            String description,
            Double workload,
            String clusterId,
            String type
    ) {}

    /**
     * POST /api/tasks
     * Creates a new rescue task in PENDING status and publishes a TASK_CREATED event.
     */
    @PostMapping
    public ResponseEntity<RescueTaskEntity> createTask(@RequestBody CreateTaskRequest req) {
        if (req == null || req.description() == null || req.description().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        double workload = (req.workload() != null && req.workload() > 0) ? req.workload() : 1.0;
        String cluster = (req.clusterId() != null && !req.clusterId().isBlank()) ? req.clusterId() : "Cluster-A";
        String type = (req.type() != null && !req.type().isBlank()) ? req.type() : "General";

        RescueTaskEntity entity = new RescueTaskEntity(
                req.taskId(),
                req.description(),
                workload,
                cluster,
                type,
                "PENDING",
                null
        );

        RescueTaskEntity created = loadBalancerService.createTask(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/tasks
     * Returns all rescue tasks, with flexible filtering by status, cluster (cluster/clusterId), and category type.
     */
    @GetMapping
    public List<RescueTaskEntity> getTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cluster,
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String type) {
        String effectiveCluster = (cluster != null && !cluster.isBlank()) ? cluster : clusterId;
        return loadBalancerService.getAllTasks(status, effectiveCluster, type);
    }

    /**
     * POST /api/tasks/{id}/assign
     * Assigns a single task to the optimal candidate node by lowest projected utilization.
     */
    @PostMapping("/{id}/assign")
    public ResponseEntity<AssignmentResult> assignTask(@PathVariable String id) {
        try {
            AssignmentResult result = loadBalancerService.assignTaskEntity(id);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/tasks/seed
     * Seeds legacy sample tasks (Medical, Food, Search...) in PENDING status.
     */
    @PostMapping("/seed")
    public ResponseEntity<List<RescueTaskEntity>> seedTasks() {
        List<RescueTaskEntity> seeded = loadBalancerService.seedSampleTasks();
        return ResponseEntity.ok(seeded);
    }

    /**
     * POST /api/tasks/pipeline/partitioned
     * Generates and assigns rescue tasks across partitioned clusters under their elected leaders (Day 4 Pipeline).
     */
    @PostMapping("/pipeline/partitioned")
    public ResponseEntity<List<AssignmentResult>> generateAndAssignPartitionedTasks() {
        List<AssignmentResult> results = clusterTaskService.generateAndAssignPartitionedTasks();
        return ResponseEntity.ok(results);
    }

    /**
     * GET /api/tasks/pipeline/summary
     * Returns an aggregated status of tasks, workloads, and cluster leaders in the pipeline.
     */
    @GetMapping("/pipeline/summary")
    public ResponseEntity<Map<String, Object>> getPipelineSummary() {
        return ResponseEntity.ok(clusterTaskService.getPipelineSummary());
    }

    /**
     * DELETE /api/tasks
     * Clears all tasks from database and resets candidate node loads for scenario reset.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> clearTasks() {
        loadBalancerService.deleteAllTasks();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "All rescue tasks deleted and simulation reset successfully"
        ));
    }
}
