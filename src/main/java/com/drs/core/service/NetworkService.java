package com.drs.core.service;

import com.drs.core.event.EventPublisher;
import com.drs.core.model.NodeEntity;
import com.drs.core.model.NodeEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exp 3: Network partition control service (replaces legacy {@code client.LocalBus}).
 * <p>
 * Simulates network isolation between clusters in a disaster zone:
 * <ul>
 *   <li>Cluster-A and Cluster-B cannot exchange messages while partitioned.</li>
 *   <li>The Cluster Coordinator becomes unreachable for edge/UAV nodes.</li>
 * </ul>
 * Publishes {@code NETWORK_PARTITIONED} and {@code NETWORK_HEALED} events on module {@code "core"}.
 */
@Service
public class NetworkService {

    private static final Logger log = LoggerFactory.getLogger(NetworkService.class);

    private final EventPublisher events;
    private final NodeEntityRepository nodeRepo;

    /** Current partition state. */
    private volatile boolean partitioned = false;

    /** In-memory cluster mappings: nodeId -> clusterId (default topology). */
    private final Map<String, String> nodeToCluster = new ConcurrentHashMap<>();

    public NetworkService(EventPublisher events, NodeEntityRepository nodeRepo) {
        this.events = events;
        this.nodeRepo = nodeRepo;
        initDefaultTopology();
    }

    private void initDefaultTopology() {
        nodeToCluster.put("UAV-01", "Cluster-A");
        nodeToCluster.put("UAV-02", "Cluster-A");
        nodeToCluster.put("Edge-01", "Cluster-B");
    }

    // ── DTOs ──

    public record NetworkStatus(
            boolean partitioned,
            List<String> clusters,
            Map<String, String> nodeClusters,
            boolean coordinatorReachable,
            String message,
            List<String> steps
    ) { }

    // ── Control Methods ──

    /**
     * Declares a network partition between clusters.
     */
    public synchronized NetworkStatus partition() {
        this.partitioned = true;
        refreshClustersFromRepo();

        List<String> steps = new ArrayList<>();
        steps.add("[NETWORK] Partition triggered: wireless link between clusters severed");
        steps.add("[NETWORK] Cluster-A (UAV nodes) and Cluster-B (Edge nodes) are isolated");
        steps.add("[NETWORK] Coordinator is UNREACHABLE for all edge nodes");
        steps.add("[NETWORK] Local leader election (Exp 4) and partitioned logging (Exp 3/5) required");

        NetworkStatus status = buildStatus(
                "Network partitioned: Cluster-A and Cluster-B are isolated. Coordinator unreachable.",
                steps
        );

        events.publish("core", "NETWORK_PARTITIONED", status);
        log.warn("Network partitioned: inter-cluster traffic and coordinator blocked");
        return status;
    }

    /**
     * Restores full network connectivity across all clusters and the coordinator.
     */
    public synchronized NetworkStatus heal() {
        this.partitioned = false;
        refreshClustersFromRepo();

        List<String> steps = new ArrayList<>();
        steps.add("[NETWORK] Connectivity restored across all clusters");
        steps.add("[NETWORK] Cluster-A and Cluster-B can exchange messages");
        steps.add("[NETWORK] Coordinator is REACHABLE: heartbeats and clock sync resumed");
        steps.add("[NETWORK] Triggering CRDT / vector clock reconciliation (Exp 5)");

        NetworkStatus status = buildStatus(
                "Network healed: all clusters and coordinator fully connected.",
                steps
        );

        events.publish("core", "NETWORK_HEALED", status);
        log.info("Network healed: full connectivity restored");
        return status;
    }

    /**
     * Query current network status.
     */
    public NetworkStatus getStatus() {
        refreshClustersFromRepo();
        List<String> steps = new ArrayList<>();
        steps.add(partitioned
                ? "[NETWORK] Status check: PARTITION ACTIVE (inter-cluster links down)"
                : "[NETWORK] Status check: NORMAL (all links operational)");
        return buildStatus(
                partitioned ? "Network is partitioned" : "Network is normal",
                steps
        );
    }

    // ── Communication Checks ──

    public boolean isPartitioned() {
        return partitioned;
    }

    /**
     * Check whether a node can reach the coordinator.
     */
    public boolean canReachCoordinator(String nodeId) {
        return !partitioned;
    }

    /**
     * Check whether two nodes can communicate directly.
     */
    public boolean canCommunicate(String fromNode, String toNode) {
        if (!partitioned) {
            return true;
        }
        String fromCluster = getCluster(fromNode);
        String toCluster = getCluster(toNode);
        return fromCluster != null && fromCluster.equalsIgnoreCase(toCluster);
    }

    public String getCluster(String nodeId) {
        String cluster = nodeToCluster.get(nodeId);
        if (cluster != null) return cluster;
        // Check DB
        return nodeRepo.findById(nodeId).map(NodeEntity::getClusterId).orElse("Cluster-A");
    }

    public void setNodeCluster(String nodeId, String clusterId) {
        nodeToCluster.put(nodeId, clusterId);
    }

    private void refreshClustersFromRepo() {
        try {
            for (NodeEntity ne : nodeRepo.findAll()) {
                if (ne.getClusterId() != null) {
                    nodeToCluster.put(ne.getNodeId(), ne.getClusterId());
                }
            }
        } catch (Exception ignored) { }
    }

    private NetworkStatus buildStatus(String message, List<String> steps) {
        Set<String> clusterSet = new TreeSet<>(nodeToCluster.values());
        if (clusterSet.isEmpty()) {
            clusterSet.addAll(List.of("Cluster-A", "Cluster-B"));
        }
        return new NetworkStatus(
                partitioned,
                new ArrayList<>(clusterSet),
                new TreeMap<>(nodeToCluster),
                !partitioned,
                message,
                steps
        );
    }
}

