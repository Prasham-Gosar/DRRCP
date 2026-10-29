package com.drs.core.service;

import com.drs.core.event.EventPublisher;
import com.drs.core.model.NodeEntity;
import com.drs.core.model.NodeEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Node Registration & Heartbeat Service (Exp 1 & 2).
 */
@Service
public class NodeRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(NodeRegistrationService.class);

    private final NodeEntityRepository repo;
    private final HeartbeatPool pool;
    private final EventPublisher events;

    public NodeRegistrationService(NodeEntityRepository repo,
                                   HeartbeatPool pool,
                                   EventPublisher events) {
        this.repo = repo;
        this.pool = pool;
        this.events = events;
    }
    private final EventPublisher eventPublisher;

    private final AtomicInteger threadCounter = new AtomicInteger(1);
    private final ExecutorService workerPool;

    public record RegistrationResult(String nodeId, String state, int connectivityScore,
                                     String thread, List<String> steps) { }

    public record HeartbeatResult(String nodeId, long timestamp, String thread,
                                  List<String> steps) { }

    public RegistrationResult registerNode(String nodeId, int battery, int signal,
                                           String nodeType, String clusterId) {
        RegistrationResult result = pool.submit(() -> {
            List<String> steps = new ArrayList<>();
            steps.add("Received registration request for " + nodeId);

            NodeEntity node = repo.findById(nodeId).orElse(null);
            if (node == null) {
                node = new NodeEntity(nodeId, battery, signal, nodeType, clusterId);
                steps.add("New node created");
            } else {
                node.setNodeType(nodeType);
                node.setClusterId(clusterId);
                node.setBattery(battery);
                node.setSignal(signal);
                node.setLastHeartbeat(Instant.now());
                node.setStatus("ALIVE");
                steps.add("Existing node updated");
            }

            int score = node.getConnectivityScore();
            String state = node.getState();
            steps.add("Connectivity score = 2×" + signal + " + " + battery + " = " + score);
            steps.add("State → " + state);

            repo.save(node);
            String thread = Thread.currentThread().getName();
            steps.add("Processed on thread [" + thread + "]");

            return new RegistrationResult(nodeId, state, score, thread, steps);
        });

        events.publish("core", "NODE_REGISTERED", result);
        log.debug("Registered node {}", nodeId);
        return result;
    public record HeartbeatResult(String nodeId, String thread, List<String> steps) { }

    public NodeRegistrationService(NodeEntityRepository repo, EventPublisher eventPublisher) {
        this.repo = repo;
        this.eventPublisher = eventPublisher;
        this.workerPool = Executors.newFixedThreadPool(4, r ->
                new Thread(r, "heartbeat-pool-" + threadCounter.getAndIncrement()));
    }

    public RegistrationResult registerNode(String nodeId, int battery, int signal,
                                           String nodeType, String clusterId) {
        Future<RegistrationResult> future = workerPool.submit(() -> {
            List<String> steps = new ArrayList<>();
            steps.add("Received registration request for node " + nodeId);

            NodeEntity entity = repo.findById(nodeId).orElse(null);
            if (entity == null) {
                entity = new NodeEntity(nodeId, battery, signal, nodeType, clusterId);
                steps.add("Created new registration for node " + nodeId);
            } else {
                entity.setBattery(battery);
                entity.setSignal(signal);
                entity.setNodeType(nodeType);
                entity.setClusterId(clusterId);
                entity.setStatus("ALIVE");
                entity.setLastHeartbeat(Instant.now());
                steps.add("Updated existing node " + nodeId);
            }

            int score = entity.getConnectivityScore();
            String state = entity.getState();
            String thread = Thread.currentThread().getName();
            entity.setLastThreadName(thread);
            steps.add("Calculated connectivity score: " + score + " -> State: " + state);
            steps.add("Handled on thread " + thread);

            repo.save(entity);
            return new RegistrationResult(nodeId, state, score, thread, steps);
        });

        try {
            RegistrationResult res = future.get(5, TimeUnit.SECONDS);
            eventPublisher.publish("coordinator", "NODE_REGISTERED", res);
            return res;
        } catch (Exception e) {
            log.error("Failed to register node {}: {}", nodeId, e.getMessage());
            return new RegistrationResult(nodeId, "ERROR", 0, Thread.currentThread().getName(),
                    List.of("Registration failed: " + e.getMessage()));
        }
    }

    public HeartbeatResult sendHeartbeat(String nodeId) {
        return sendHeartbeat(nodeId, System.currentTimeMillis());
    }

    public HeartbeatResult sendHeartbeat(String nodeId, long timestamp) {
        HeartbeatResult result = pool.submit(() -> {
            List<String> steps = new ArrayList<>();
            String thread = Thread.currentThread().getName();
            steps.add("Heartbeat from " + nodeId + " processed on [" + thread + "]");

            Optional<NodeEntity> opt = repo.findById(nodeId);
            if (opt.isPresent()) {
                NodeEntity node = opt.get();
                node.setLastHeartbeat(Instant.ofEpochMilli(timestamp));
                node.setLastThreadName(thread);
                node.setStatus("ALIVE");
                repo.save(node);
                steps.add("Last heartbeat updated to " + Instant.ofEpochMilli(timestamp));
            } else {
                steps.add("WARNING: node " + nodeId + " is not registered");
            }

            return new HeartbeatResult(nodeId, timestamp, thread, steps);
        });

        events.publish("core", "HEARTBEAT", result);
        return result;
    public HeartbeatResult sendHeartbeat(String nodeId, long timestampMillis) {
        Future<HeartbeatResult> future = workerPool.submit(() -> {
            List<String> steps = new ArrayList<>();
            String thread = Thread.currentThread().getName();

            NodeEntity entity = repo.findById(nodeId).orElse(null);
            if (entity == null) {
                steps.add("WARNING: Heartbeat received for unregistered node " + nodeId);
                return new HeartbeatResult(nodeId, thread, steps);
            }

            entity.setLastHeartbeat(Instant.ofEpochMilli(timestampMillis));
            entity.setLastThreadName(thread);
            entity.setStatus("ALIVE");
            repo.save(entity);

            steps.add("Heartbeat processed for " + nodeId + " on thread [" + thread + "]");
            return new HeartbeatResult(nodeId, thread, steps);
        });

        try {
            HeartbeatResult res = future.get(5, TimeUnit.SECONDS);
            eventPublisher.publish("coordinator", "NODE_HEARTBEAT", res);
            return res;
        } catch (Exception e) {
            log.error("Failed heartbeat for node {}: {}", nodeId, e.getMessage());
            return new HeartbeatResult(nodeId, Thread.currentThread().getName(),
                    List.of("Heartbeat failed: " + e.getMessage()));
        }
    }

    public List<NodeEntity> getAllNodes() {
        return repo.findAll();
    }

    public Optional<NodeEntity> getNode(String nodeId) {
        return repo.findById(nodeId);
    }
}
}

