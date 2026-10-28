package com.drs.core.service;

import com.drs.core.event.EventPublisher;
import com.drs.core.model.NodeEntity;
import com.drs.core.model.NodeEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Silent Node Monitor (Exp 2).
 * Periodically identifies nodes whose heartbeats have ceased beyond thresholdMs.
 */
@Service
public class SilentNodeMonitor {

    private static final Logger log = LoggerFactory.getLogger(SilentNodeMonitor.class);

    private final NodeEntityRepository repo;
    private final EventPublisher eventPublisher;

    private long thresholdMs = 6000;
    private final Set<String> alertedNodes = ConcurrentHashMap.newKeySet();

    public record SilentNodeAlert(String nodeId, long silentForMs, Instant lastSeen) { }

    public SilentNodeMonitor(NodeEntityRepository repo, EventPublisher eventPublisher) {
        this.repo = repo;
        this.eventPublisher = eventPublisher;
    }

    public long getThresholdMs() {
        return thresholdMs;
    }

    public void setThresholdMs(long thresholdMs) {
        this.thresholdMs = thresholdMs;
    }

    public void clearAlert(String nodeId) {
        alertedNodes.remove(nodeId);
    }

    @Scheduled(fixedRate = 2000)
    public List<SilentNodeAlert> checkForSilentNodes() {
        List<SilentNodeAlert> alerts = new ArrayList<>();
        Instant now = Instant.now();

        List<NodeEntity> nodes = repo.findAll();
        for (NodeEntity node : nodes) {
            if (node.getLastHeartbeat() == null) continue;

            long silentFor = now.toEpochMilli() - node.getLastHeartbeat().toEpochMilli();
            if (silentFor > thresholdMs) {
                node.setStatus("SILENT");
                repo.save(node);

                if (alertedNodes.add(node.getNodeId())) {
                    SilentNodeAlert alert = new SilentNodeAlert(node.getNodeId(), silentFor, node.getLastHeartbeat());
                    alerts.add(alert);
                    eventPublisher.publish("coordinator", "NODE_SILENT", alert);
                    log.warn("Node {} flagged SILENT (inactive for {} ms)", node.getNodeId(), silentFor);
                }
            } else {
                alertedNodes.remove(node.getNodeId());
            }
        }

        return alerts;
    }
}

