package com.drs.core.controller;

import com.drs.core.service.ClockSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Exp 3: Cristian's clock synchronization and vector clock inspection.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code POST /api/clock/sync/{nodeId}} — synchronize a single node</li>
 *   <li>{@code POST /api/clock/sync-all} — synchronize all nodes</li>
 *   <li>{@code GET  /api/clock/status} — status overview of physical and logical clocks</li>
 *   <li>{@code POST /api/clock/skew/{nodeId}} — adjust injected drift in milliseconds</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/clock")
public class ClockController {

    private final ClockSyncService clockSyncService;

    public ClockController(ClockSyncService clockSyncService) {
        this.clockSyncService = clockSyncService;
    }

    @PostMapping("/sync/{nodeId}")
    public ResponseEntity<ClockSyncService.NodeClockSyncResult> syncNode(@PathVariable String nodeId) {
        ClockSyncService.NodeClockSyncResult result = clockSyncService.syncNode(nodeId);
        if (!result.success()) {
            return ResponseEntity.status(409).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sync-all")
    public List<ClockSyncService.NodeClockSyncResult> syncAll() {
        return clockSyncService.syncAll();
    }

    @GetMapping("/status")
    public ClockSyncService.ClockStatusResponse getStatus() {
        return clockSyncService.getClockStatus();
    }

    public record SkewRequest(long skewMillis) { }

    @PostMapping("/skew/{nodeId}")
    public ResponseEntity<Void> setSkew(
            @PathVariable String nodeId,
            @RequestParam(required = false) Long skew,
            @RequestBody(required = false) SkewRequest body) {
        long effectiveSkew = 0L;
        if (skew != null) {
            effectiveSkew = skew;
        } else if (body != null) {
            effectiveSkew = body.skewMillis();
        }
        clockSyncService.setInjectedSkew(nodeId, effectiveSkew);
        return ResponseEntity.ok().build();
    }
}

