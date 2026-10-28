package com.drs.faulttolerance.controller;

import com.drs.faulttolerance.model.FaultToleranceResult;
import com.drs.faulttolerance.service.FaultToleranceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

/**
 * Thin REST controller for Exp 8: Primary-Backup Fault Tolerance.
 * <p>
 * All logic lives in {@link FaultToleranceService}.
 */
@RestController
@RequestMapping("/api/ft")
public class FaultToleranceController {

    private final FaultToleranceService service;

    public FaultToleranceController(FaultToleranceService service) {
        this.service = service;
    }

    // ── Request DTOs ──

    public record UpdateRequest(@NotBlank String resourceId,
                                @NotBlank String updateType,
                                @NotBlank String value,
                                @NotBlank String sourceNode) { }

    // ── Endpoints ──

    /** GET /api/ft/state — current system state without mutation. */
    @GetMapping("/state")
    public FaultToleranceResult getState() {
        return service.getState();
    }

    /** POST /api/ft/updates — process a new system update through primary→backup replication. */
    @PostMapping("/updates")
    public FaultToleranceResult processUpdate(@Valid @RequestBody UpdateRequest req) {
        return service.processUpdate(req.resourceId(), req.updateType(),
                req.value(), req.sourceNode());
    }

    /** POST /api/ft/primary/fail — simulate primary coordinator failure. */
    @PostMapping("/primary/fail")
    public FaultToleranceResult failPrimary() {
        return service.failPrimary();
    }

    /** POST /api/ft/reset — reset the subsystem to initial state. */
    @PostMapping("/reset")
    public FaultToleranceResult reset() {
        return service.reset();
    }
}
