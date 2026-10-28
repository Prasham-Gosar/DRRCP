package com.drs.core.service;

import com.drs.core.event.EventPublisher;
import com.drs.core.model.NodeEntity;
import com.drs.core.model.NodeEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

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
    }

    public List<NodeEntity> getAllNodes() {
        return repo.findAll();
    }

    public Optional<NodeEntity> getNode(String nodeId) {
        return repo.findById(nodeId);
    }
}