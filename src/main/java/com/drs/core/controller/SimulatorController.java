package com.drs.core.controller;

import com.drs.core.service.NodeSimulator;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    private final NodeSimulator simulator;

    public SimulatorController(NodeSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/start")
    public NodeSimulator.SimulatorStatus start() {
        return simulator.start();
    }

    @PostMapping("/stop")
    public NodeSimulator.SimulatorStatus stop() {
        return simulator.stop();
    }

    @PostMapping("/silence/{nodeId}")
    public NodeSimulator.SimulatorStatus silence(@PathVariable String nodeId) {
        return simulator.silence(nodeId);
    }

    @GetMapping("/status")
    public NodeSimulator.SimulatorStatus status() {
        return new NodeSimulator.SimulatorStatus(
                simulator.isRunning(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(simulator.isRunning() ? "Simulator is running" : "Simulator is stopped"));
    }
}