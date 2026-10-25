package com.drs.core.service;

import com.drs.core.event.EventPublisher;
import com.drs.core.model.NodeStatus;
import com.drs.core.model.NodeStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Ported from legacy {@code server.ClusterCoordinatorImpl}.
 * <p>
 * Exp 1 — registerNode (connectivity scoring) <br>
 * Exp 2 — sendHeartbeat (thread-pool dispatch) + checkForSilentNodes (scheduled monitor) <br>
 * Exp 3 — getTime (Cristian's Algorithm time-server side)
 */
@Service
public class CoordinatorService {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorService.class);

    /** Same threshold as legacy (6000 ms). */
    private static final long SILENCE_THRESHOLD_MS = 6000;

    private final NodeStatusRepository repo;
    private final EventPublisher events;

    /** Nodes already reported as silent, so we alert once per silence instead of every second. */
    private final Set<String> alreadyAlerted = ConcurrentHashMap.newKeySet();

    /** Exp 2: fixed worker pool (4 threads), same as legacy. */
    private final ExecutorService workerPool = Executors.newFixedThreadPool(4);

    public CoordinatorService(NodeStatusRepository repo, EventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    // ─── Exp 1: Registration + connectivity scoring ───

    public record RegistrationResult(String nodeId, String state, int connectivityScore,
                                     String thread, List<String> steps) { }

    /**
     * Register a node. Runs the scoring on the worker pool, exactly like legacy.
     */
    public RegistrationResult registerNode(String nodeId, int battery, int signal,
                                           String nodeType, String clusterId) {
        Future<RegistrationResult> future = workerPool.submit(() -> {
            List<String> steps = new ArrayList<>();
            steps.add("Received registration request for " + nodeId);

            NodeStatus node = repo.findById(nodeId).orElse(null);
            if (node == null) {
                node = new NodeStatus(nodeId, battery, signal, nodeType, clusterId);
                steps.add("New node created");
            } else {
                node.setNodeType(nodeType);
                node.setClusterId(clusterId);
                node.setBatteryLevel(battery);
                node.setSignalStrength(signal);
                node.setLastHeartbeat(Instant.now());
                steps.add("Existing node updated");
            }

            int score = node.getConnectivityScore();
            String state = node.getState();
            steps.add("Connectivity score = 2×" + signal + " + " + battery + " = " + score);
            steps.add("State → " + state);

            repo.save(node);
            alreadyAlerted.remove(nodeId);
            String thread = Thread.currentThread().getName();
            steps.add("Processed on thread [" + thread + "]");

            return new RegistrationResult(nodeId, state, score, thread, steps);
        });

        try {
            RegistrationResult result = future.get(10, TimeUnit.SECONDS);
            events.publish("coordinator", "NODE_REGISTERED", result);
            return result;
        } catch (Exception e) {
            log.error("Worker pool failed for registration of {}: {}", nodeId, e.getMessage());
            return new RegistrationResult(nodeId, "ERROR", 0, "N/A",
                    List.of("ERROR: worker pool failed (" + e.getMessage() + ")"));
        }
    }

    // ─── Exp 1: Get node state ───

    public String getNodeState(String nodeId) {
        return repo.findById(nodeId).map(NodeStatus::getState).orElse(null);
    }

    public List<NodeStatus> getAllNodes() {
        return repo.findAll();
    }

    public Optional<NodeStatus> getNode(String nodeId) {
        return repo.findById(nodeId);
    }

    // ─── Exp 2: Heartbeat ───

    public record HeartbeatResult(String nodeId, long timestamp, String thread,
                                  List<String> steps) { }

    /**
     * Process a heartbeat on the worker pool, same pattern as legacy.
     */
    public HeartbeatResult sendHeartbeat(String nodeId) {
        return sendHeartbeat(nodeId, System.currentTimeMillis());
    }

    /** Same as legacy {@code sendHeartbeat(nodeId, timestamp)}: the node supplies its own timestamp. */
    public HeartbeatResult sendHeartbeat(String nodeId, long timestamp) {

        Future<HeartbeatResult> future = workerPool.submit(() -> {
            List<String> steps = new ArrayList<>();
            String thread = Thread.currentThread().getName();
            steps.add("Heartbeat from " + nodeId + " processed on [" + thread + "]");

            Optional<NodeStatus> opt = repo.findById(nodeId);
            if (opt.isPresent()) {
                NodeStatus node = opt.get();
                node.setLastHeartbeat(Instant.ofEpochMilli(timestamp));
                repo.save(node);
                alreadyAlerted.remove(nodeId);
                steps.add("Last heartbeat updated to " + Instant.ofEpochMilli(timestamp));
            } else {
                steps.add("WARNING: node " + nodeId + " is not registered");
            }

            return new HeartbeatResult(nodeId, timestamp, thread, steps);
        });

        try {
            HeartbeatResult result = future.get(10, TimeUnit.SECONDS);
            events.publish("heartbeat", "HEARTBEAT_RECEIVED", result);
            return result;
        } catch (Exception e) {
            log.error("Heartbeat processing failed for {}: {}", nodeId, e.getMessage());
            return new HeartbeatResult(nodeId, timestamp, "N/A",
                    List.of("ERROR: " + e.getMessage()));
        }
    }

    // ─── Exp 2: Silent-node monitor (scheduled) ───

    public record SilentNodeAlert(String nodeId, long silentForMs, long thresholdMs) { }

    /**
     * Runs every 1 second (same as legacy scheduler). Checks all registered nodes
     * for silence exceeding the threshold and publishes alerts.
     * <p>
     * Web-edition change: the legacy console printed the warning every second; here the
     * alert is published once per silence episode (and again if the node recovers and
     * goes silent again), otherwise the event log would grow by one row per second.
     *
     * @return the alerts raised by this run (handy for tests)
     */
    public List<SilentNodeAlert> checkForSilentNodes() {
        long now = System.currentTimeMillis();
        List<SilentNodeAlert> raised = new ArrayList<>();

        for (NodeStatus node : repo.findAll()) {
            if (node.getLastHeartbeat() == null) continue;
            long silentFor = now - node.getLastHeartbeat().toEpochMilli();
            if (silentFor > SILENCE_THRESHOLD_MS) {
                if (alreadyAlerted.add(node.getNodeId())) {
                    SilentNodeAlert alert = new SilentNodeAlert(node.getNodeId(), silentFor, SILENCE_THRESHOLD_MS);
                    events.publish("heartbeat", "PARTITION_SUSPECTED", alert);
                    raised.add(alert);
                    log.debug("PARTITION SUSPECTED: node '{}' silent for {}ms", node.getNodeId(), silentFor);
                }
            } else {
                alreadyAlerted.remove(node.getNodeId());
            }
        }
        return raised;
    }

    /** Spring entry point: fires the monitor every second (legacy: scheduleAtFixedRate 1s). */
    @Scheduled(fixedRate = 1000, initialDelay = 1000)
    void scheduledSilenceCheck() {
        checkForSilentNodes();
    }

    // ─── Exp 3: Cristian's Algorithm — time server ───

    public record TimeResponse(long serverTime, String note) { }

    /**
     * Returns the current server time after a small random processing delay,
     * exactly like legacy.
     */
    public TimeResponse getTime() {
        try {
            // Small random processing delay, same as legacy (10-60ms)
            Thread.sleep(ThreadLocalRandom.current().nextInt(10, 60));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        long serverTime = System.currentTimeMillis();
        return new TimeResponse(serverTime, "Cristian's Algorithm time-server response");
    }
}
