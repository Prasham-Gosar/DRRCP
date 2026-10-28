package com.drs.core.service;

import com.drs.core.model.NodeStatus;
import com.drs.core.rmi.NodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NodeSimulator {

    private static final Logger log = LoggerFactory.getLogger(NodeSimulator.class);

    private volatile boolean running = false;
    private final Map<String, Thread> threads = new ConcurrentHashMap<>();
    private final Set<String> silenced = ConcurrentHashMap.newKeySet();
    private final NodeRegistrationService registrationService;

    public NodeSimulator(NodeRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    private record SimNode(String nodeId, int battery, int signal,
                           String nodeType, String clusterId) { }

    private static final List<SimNode> NODES = List.of(
            new SimNode("UAV-01", 85, 75, "UAV", "Cluster-A"),
            new SimNode("UAV-02", 60, 50, "UAV", "Cluster-A"),
            new SimNode("Edge-01", 95, 90, "EDGE", "Cluster-B")
    );

    public record SimulatorStatus(boolean running, List<String> activeNodes,
                                  List<String> silencedNodes, List<String> steps) { }

    public SimulatorStatus start() {
        if (running) {
            return new SimulatorStatus(true,
                    List.copyOf(threads.keySet()),
                    List.copyOf(silenced),
                    List.of("Simulator is already running."));
        }

        running = true;
        silenced.clear();
        List<String> steps = new ArrayList<>();

        NodeService rmiStub = null;
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            rmiStub = (NodeService) registry.lookup("Coordinator");
            steps.add("Connected to RMI registry on port 1099");
        } catch (Exception e) {
            steps.add("RMI unavailable (" + e.getMessage() + "), using REST fallback");
        }

        for (SimNode sn : NODES) {
            try {
                if (rmiStub != null) {
                    NodeStatus ns = new NodeStatus(
                            sn.nodeId(), sn.battery(), sn.signal(),
                            sn.nodeType(), sn.clusterId());
                    String reply = rmiStub.registerNode(ns);
                    steps.add("[RMI] " + reply);
                } else {
                    var result = registrationService.registerNode(
                            sn.nodeId(), sn.battery(), sn.signal(),
                            sn.nodeType(), sn.clusterId());
                    steps.add("[REST] Registered " + result.nodeId() + " — score "
                              + result.connectivityScore() + " (" + result.state() + ")");
                }
            } catch (Exception e) {
                steps.add("Failed to register " + sn.nodeId() + ": " + e.getMessage());
            }

            final NodeService stub = rmiStub;
            Thread t = new Thread(() -> heartbeatLoop(sn, stub), "sim-" + sn.nodeId());
            t.setDaemon(true);
            t.start();
            threads.put(sn.nodeId(), t);
            steps.add("Heartbeat thread started for " + sn.nodeId());
        }

        return new SimulatorStatus(true, List.copyOf(threads.keySet()),
                List.copyOf(silenced), steps);
    }

    public SimulatorStatus stop() {
        running = false;
        threads.values().forEach(Thread::interrupt);
        List<String> stopped = List.copyOf(threads.keySet());
        threads.clear();
        silenced.clear();
        return new SimulatorStatus(false, List.of(), List.of(),
                List.of("Simulator stopped. Interrupted threads: " + stopped));
    }

    public SimulatorStatus silence(String nodeId) {
        List<String> steps = new ArrayList<>();
        if (!threads.containsKey(nodeId)) {
            steps.add("Node " + nodeId + " is not in the simulator.");
        } else if (silenced.contains(nodeId)) {
            steps.add("Node " + nodeId + " is already silenced.");
        } else {
            silenced.add(nodeId);
            steps.add("Silenced " + nodeId + " — heartbeats paused. "
                       + "Silent detection should trigger in ~6 s.");
        }
        return new SimulatorStatus(running, List.copyOf(threads.keySet()),
                List.copyOf(silenced), steps);
    }

    public boolean isRunning() {
        return running;
    }

    private void heartbeatLoop(SimNode sn, NodeService rmiStub) {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(2000);
                if (silenced.contains(sn.nodeId())) {
                    continue;
                }

                long ts = System.currentTimeMillis();
                if (rmiStub != null) {
                    try {
                        rmiStub.sendHeartbeat(sn.nodeId(), ts);
                    } catch (Exception e) {
                        registrationService.sendHeartbeat(sn.nodeId(), ts);
                    }
                } else {
                    registrationService.sendHeartbeat(sn.nodeId(), ts);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Heartbeat failed for {}: {}", sn.nodeId(), e.getMessage());
            }
        }
    }
}