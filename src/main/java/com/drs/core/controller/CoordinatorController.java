package com.drs.core.controller;

import com.drs.core.model.NodeStatus;
import com.drs.core.service.CoordinatorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Thin REST controller for Exp 1, 2, 3.
 * All logic lives in {@link CoordinatorService}.
 */
@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorController {

    private final CoordinatorService service;

    public CoordinatorController(CoordinatorService service) {
        this.service = service;
    }

    // ─── Exp 1: Registration ───

    public record RegisterRequest(@NotBlank String nodeId,
                                  @Min(0) @Max(100) int batteryLevel,
                                  @Min(0) @Max(100) int signalStrength,
                                  @NotBlank String nodeType,
                                  @NotBlank String clusterId) { }

    @PostMapping("/register")
    public CoordinatorService.RegistrationResult register(@Valid @RequestBody RegisterRequest req) {
        return service.registerNode(req.nodeId(), req.batteryLevel(), req.signalStrength(),
                req.nodeType(), req.clusterId());
    }

    @GetMapping("/nodes")
    public List<NodeStatus> allNodes() {
        return service.getAllNodes();
    }

    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<NodeStatus> getNode(@PathVariable String nodeId) {
        return service.getNode(nodeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/nodes/{nodeId}/state")
    public ResponseEntity<Map<String, String>> getNodeState(@PathVariable String nodeId) {
        String state = service.getNodeState(nodeId);
        if (state == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("nodeId", nodeId, "state", state));
    }

    // ─── Exp 2: Heartbeat ───

    @PostMapping("/heartbeat/{nodeId}")
    public CoordinatorService.HeartbeatResult heartbeat(@PathVariable String nodeId,
                                                        @RequestParam(required = false) Long timestamp) {
        return timestamp == null
                ? service.sendHeartbeat(nodeId)
                : service.sendHeartbeat(nodeId, timestamp);
    }

    // ─── Exp 3: Cristian's Time Server ───

    @GetMapping("/time")
    public CoordinatorService.TimeResponse time() {
        return service.getTime();
    }
}
