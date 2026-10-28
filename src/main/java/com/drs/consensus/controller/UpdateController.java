package com.drs.consensus.controller;

import com.drs.consensus.model.UpdateRecord;
import com.drs.consensus.model.VectorClock;
import com.drs.consensus.reconcile.UpdateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Exp 5 Update Management and Vector Clock stamping.
 */
@RestController
@RequestMapping("/api/updates")
public class UpdateController {

    private final UpdateService updateService;

    public UpdateController(UpdateService updateService) {
        this.updateService = updateService;
    }

    public record CreateUpdateRequest(
            @NotBlank(message = "resourceKey is required") String resourceKey,
            @NotBlank(message = "resourceType is required") String resourceType,
            @NotBlank(message = "value is required") String value,
            @NotBlank(message = "clusterId is required") String clusterId,
            @NotBlank(message = "nodeId is required") String nodeId,
            String description) {
    }

    /**
     * POST /api/updates — Stamp vector clock and store an update record.
     */
    @PostMapping
    public ResponseEntity<UpdateRecord> createUpdate(@Valid @RequestBody CreateUpdateRequest req) {
        UpdateRecord record = updateService.recordUpdate(
                req.resourceKey(),
                req.resourceType(),
                req.value(),
                req.clusterId(),
                req.nodeId(),
                req.description() != null ? req.description() : "");
        return ResponseEntity.ok(record);
    }

    /**
     * GET /api/updates — List all stored updates in order.
     */
    @GetMapping
    public ResponseEntity<List<UpdateRecord>> getAllUpdates() {
        return ResponseEntity.ok(updateService.getAllUpdates());
    }

    /**
     * DELETE /api/updates — Clear all updates and reset clocks.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> resetUpdates() {
        updateService.resetUpdates();
        return ResponseEntity.ok(Map.of("message", "All updates cleared successfully"));
    }

    /**
     * POST /api/updates/seed — Pre-populate sample disaster partition updates.
     */
    @PostMapping("/seed")
    public ResponseEntity<List<UpdateRecord>> seedScenario() {
        return ResponseEntity.ok(updateService.seedScenarioUpdates());
    }

    /**
     * GET /api/updates/compare — Compare vector clocks of two updates by ID.
     */
    @GetMapping("/compare")
    public ResponseEntity<?> compareUpdates(@RequestParam("id1") String id1,
            @RequestParam("id2") String id2) {
        List<UpdateRecord> all = updateService.getAllUpdates();
        UpdateRecord u1 = all.stream().filter(u -> u.getRecordId().equalsIgnoreCase(id1)).findFirst().orElse(null);
        UpdateRecord u2 = all.stream().filter(u -> u.getRecordId().equalsIgnoreCase(id2)).findFirst().orElse(null);

        if (u1 == null || u2 == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "One or both update IDs not found"));
        }

        VectorClock.Relation relation = VectorClock.compare(u1.getVector(), u2.getVector());
        return ResponseEntity.ok(Map.of(
                "id1", u1.getRecordId(),
                "node1", u1.getNodeId(),
                "clock1", u1.getVector(),
                "id2", u2.getRecordId(),
                "node2", u2.getNodeId(),
                "clock2", u2.getVector(),
                "relation", relation.name()));
    }
}
