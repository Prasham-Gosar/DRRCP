package com.drs.core.controller;

import com.drs.core.service.NetworkService;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Exp 3: Network partition and healing controls.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code POST /api/network/partition} — simulate network partition between clusters</li>
 *   <li>{@code POST /api/network/heal} — heal network partition</li>
 *   <li>{@code GET  /api/network/status} — get current partition topology and status</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/network")
public class NetworkController {

    private final NetworkService networkService;

    public NetworkController(NetworkService networkService) {
        this.networkService = networkService;
    }

    @PostMapping("/partition")
    public NetworkService.NetworkStatus partition() {
        return networkService.partition();
    }

    @PostMapping("/heal")
    public NetworkService.NetworkStatus heal() {
        return networkService.heal();
    }

    @GetMapping("/status")
    public NetworkService.NetworkStatus getStatus() {
        return networkService.getStatus();
    }
}

