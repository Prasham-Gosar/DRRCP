package com.drs.compute.controller;

import com.drs.compute.model.AssignmentResult;
import com.drs.compute.model.LoadNode;
import com.drs.compute.model.RescueTaskEntity;
import com.drs.compute.service.LoadBalancerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Exp 6: Load Balancer operations.
 */
@RestController
@RequestMapping("/api/loadbalancer")
public class LoadBalancerController {

    private final LoadBalancerService loadBalancerService;

    @Autowired
    public LoadBalancerController(LoadBalancerService loadBalancerService) {
        this.loadBalancerService = loadBalancerService;
    }

    /**
     * GET /api/loadbalancer/nodes
     * Returns candidate nodes with: capacity, effective capacity, load, utilization, available.
     */
    @GetMapping("/nodes")
    public List<LoadNode> getNodes() {
        return loadBalancerService.getCandidateNodes();
    }

    /**
     * POST /api/loadbalancer/assign-all
     * Assigns all pending rescue tasks across candidate nodes and updates loads.
     */
    @PostMapping("/assign-all")
    public List<AssignmentResult> assignAll() {
        return loadBalancerService.assignAllPending();
    }

    /**
     * POST /api/loadbalancer/nodes/{id}/available
     * Updates candidate node availability for demoing failure & re-balancing.
     */
    @PostMapping("/nodes/{id}/available")
    public ResponseEntity<LoadNode> setAvailability(
            @PathVariable String id,
            @RequestParam boolean available) {
        try {
            LoadNode updated = loadBalancerService.setNodeAvailability(id, available);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/loadbalancer/nodes/{id}/toggle
     * Toggles availability of node {id}.
     */
    @PostMapping("/nodes/{id}/toggle")
    public ResponseEntity<LoadNode> toggleAvailability(@PathVariable String id) {
        try {
            LoadNode current = loadBalancerService.getCandidateNodes().stream()
                    .filter(n -> n.getNodeId().equalsIgnoreCase(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Node not found: " + id));

            LoadNode updated = loadBalancerService.setNodeAvailability(id, !current.isAvailable());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/loadbalancer/reset
     * Resets candidate node loads to 0.0 and restores availability.
     */
    @PostMapping("/reset")
    public List<LoadNode> resetNodes() {
        return loadBalancerService.resetNodes();
    }

    /**
     * POST /api/loadbalancer/seed
     * Helper to seed sample tasks via loadbalancer path.
     */
    @PostMapping("/seed")
    public List<RescueTaskEntity> seedTasks() {
        return loadBalancerService.seedSampleTasks();
    }
}
