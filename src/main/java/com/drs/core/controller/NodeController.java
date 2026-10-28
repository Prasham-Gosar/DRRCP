package com.drs.core.controller;

import com.drs.core.model.NodeEntity;
import com.drs.core.service.NodeRegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nodes")
public class NodeController {

    private final NodeRegistrationService service;

    public NodeController(NodeRegistrationService service) {
        this.service = service;
    }

    public record RegisterRequest(@NotBlank String nodeId,
                                  @Min(0) @Max(100) int battery,
                                  @Min(0) @Max(100) int signal,
                                  @NotBlank String nodeType,
                                  @NotBlank String clusterId) { }

    @PostMapping("/register")
    public NodeRegistrationService.RegistrationResult register(
            @Valid @RequestBody RegisterRequest req) {
        return service.registerNode(
                req.nodeId(), req.battery(), req.signal(),
                req.nodeType(), req.clusterId());
    }

    @GetMapping
    public List<NodeEntity> allNodes() {
        return service.getAllNodes();
    }

    @GetMapping("/{id}")
    public ResponseEntity<NodeEntity> getNode(@PathVariable String id) {
        return service.getNode(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/heartbeat")
    public NodeRegistrationService.HeartbeatResult heartbeat(
            @PathVariable String id,
            @RequestParam(required = false) Long timestamp) {
        return timestamp == null
                ? service.sendHeartbeat(id)
                : service.sendHeartbeat(id, timestamp);
    }
}