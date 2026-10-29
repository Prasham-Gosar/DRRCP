package com.drs.core.service;

import com.drs.consensus.reconcile.VectorClockRegistry;
import com.drs.core.event.EventPublisher;
import com.drs.core.model.NodeEntity;
import com.drs.core.model.NodeEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exp 3: Cristian's Clock Synchronization Algorithm & Vector Clock Monitor.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Simulates physical clock drift per node via {@code injectedSkewMillis}.</li>
 *   <li>Executes Cristian's Algorithm:
 *     <ol>
 *       <li>Record node local time \(t_0\) before request.</li>
 *       <li>Query Coordinator server time \(t_1\) via {@link CoordinatorService#getTime()}.</li>
 *       <li>Record node local time \(t_2\) upon response receipt.</li>
 *       <li>Round-Trip Time: \(RTT = t_2 - t_0\).</li>
 *       <li>Clock Offset: \(offset = t_1 + \frac{RTT}{2} - t_2\).</li>
 *       <li>Corrected Time: \(T_{corrected} = t_2 + offset = t_1 + \frac{RTT}{2}\).</li>
 *     </ol>
 *   </li>
 *   <li>Enforces network partition rules: Coordinator is unreachable while partitioned.</li>
 *   <li>Publishes {@code CLOCK_SYNCED} events to the SSE event bus.</li>
 *   <li>Exposes read-only snapshots of each node's {@code VectorClock} from Grasit's package.</li>
 * </ul>
 */
@Service
public class ClockSyncService {

    private static final Logger log = LoggerFactory.getLogger(ClockSyncService.class);

    private final CoordinatorService coordinator;
    private final NetworkService networkService;
    private final EventPublisher events;
    private final NodeEntityRepository nodeRepo;
    private final VectorClockRegistry vectorClockRegistry;

    /** Injected clock drift per node in milliseconds. */
    private final Map<String, Long> injectedSkews = new ConcurrentHashMap<>();

    /** History of last sync result per node. */
    private final Map<String, NodeClockSyncResult> lastSyncResults = new ConcurrentHashMap<>();

    public ClockSyncService(CoordinatorService coordinator,
                            NetworkService networkService,
                            EventPublisher events,
                            NodeEntityRepository nodeRepo,
                            VectorClockRegistry vectorClockRegistry) {
        this.coordinator = coordinator;
        this.networkService = networkService;
        this.events = events;
        this.nodeRepo = nodeRepo;
        this.vectorClockRegistry = vectorClockRegistry;

        initDefaultSkews();
    }

    private void initDefaultSkews() {
        // Values from legacy DisasterReliefClient
        injectedSkews.put("UAV-01", 1500L);   // +1.5s drift
        injectedSkews.put("UAV-02", -800L);   // -0.8s drift
        injectedSkews.put("Edge-01", 300L);   // +0.3s drift
    }

    // ── DTOs ──

    public record NodeClockSyncResult(
            String nodeId,
            String clusterId,
            boolean success,
            long injectedSkewMillis,
            long t0,
            long serverTime,
            long t2,
            long rttMillis,
            long offsetMillis,
            long rawLocalTime,
            long correctedTime,
            String lastSyncTime,
            String errorMessage,
            List<String> steps
    ) { }

    public record NodeClockStatus(
            String nodeId,
            String clusterId,
            long injectedSkewMillis,
            long rawLocalTime,
            long rttMillis,
            long offsetMillis,
            long correctedTime,
            boolean synced,
            String lastSyncTime,
            Map<String, Integer> vectorClock,
            String statusBadge
    ) { }

    public record ClockStatusResponse(
            boolean partitioned,
            List<NodeClockStatus> nodes,
            List<String> steps
    ) { }

    // ── Synchronization Logic ──

    /**
     * Synchronize a specific node's clock with the Cluster Coordinator using Cristian's Algorithm.
     */
    public NodeClockSyncResult syncNode(String nodeId) {
        List<String> steps = new ArrayList<>();
        String clusterId = networkService.getCluster(nodeId);
        long skew = injectedSkews.getOrDefault(nodeId, 0L);

        steps.add("[Cristian] Initiating clock sync for node " + nodeId + " (" + clusterId + ")");

        // Check network partition
        if (!networkService.canReachCoordinator(nodeId)) {
            String errorMsg = "Coordinator is UNREACHABLE: Network partition active between clusters";
            steps.add("[Cristian] ERROR: " + errorMsg);
            log.warn("Clock sync blocked for {}: network partitioned", nodeId);

            NodeClockSyncResult failure = new NodeClockSyncResult(
                    nodeId, clusterId, false, skew,
                    0, 0, 0, 0, 0,
                    System.currentTimeMillis() + skew,
                    0,
                    Instant.now().toString(),
                    errorMsg,
                    steps
            );
            lastSyncResults.put(nodeId, failure);
            return failure;
        }

        // Cristian's Algorithm
        long t0 = System.currentTimeMillis() + skew;
        steps.add("[Cristian] t0 (local node time before request) = " + t0 + " ms (skew = " + skew + " ms)");

        CoordinatorService.TimeResponse timeResp = coordinator.getTime();
        long t1 = timeResp.serverTime();
        steps.add("[Cristian] t1 (coordinator server time) = " + t1 + " ms");

        long t2 = System.currentTimeMillis() + skew;
        steps.add("[Cristian] t2 (local node time on receipt) = " + t2 + " ms");

        long rtt = t2 - t0;
        long oneWayDelay = rtt / 2;
        long offset = t1 + oneWayDelay - t2; // t1 + RTT/2 - t2
        long correctedTime = t2 + offset;

        steps.add("[Cristian] RTT = (t2 - t0) = " + rtt + " ms (estimated one-way delay ~ " + oneWayDelay + " ms)");
        steps.add("[Cristian] Clock Offset = t1 + RTT/2 - t2 = " + offset + " ms");
        steps.add("[Cristian] Corrected Clock Time = " + correctedTime + " ms");

        NodeClockSyncResult result = new NodeClockSyncResult(
                nodeId, clusterId, true, skew,
                t0, t1, t2, rtt, offset,
                t2, correctedTime,
                Instant.now().toString(),
                null,
                steps
        );

        lastSyncResults.put(nodeId, result);

        // Publish CLOCK_SYNCED event
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("nodeId", nodeId);
        eventPayload.put("clusterId", clusterId);
        eventPayload.put("rttMs", rtt);
        eventPayload.put("offsetMs", offset);
        eventPayload.put("skewMs", skew);
        eventPayload.put("serverTime", t1);
        eventPayload.put("correctedTime", correctedTime);
        events.publish("core", "CLOCK_SYNCED", eventPayload);

        log.info("Clock synced for {}: offset={}ms, RTT={}ms", nodeId, offset, rtt);
        return result;
    }

    /**
     * Synchronize all known nodes.
     */
    public List<NodeClockSyncResult> syncAll() {
        List<String> nodeIds = getKnownNodeIds();
        List<NodeClockSyncResult> results = new ArrayList<>();
        for (String id : nodeIds) {
            results.add(syncNode(id));
        }
        return results;
    }

    // ── Status & Queries ──

    /**
     * Get aggregate clock and vector clock status across all nodes.
     */
    public ClockStatusResponse getClockStatus() {
        List<String> nodeIds = getKnownNodeIds();
        List<NodeClockStatus> list = new ArrayList<>();
        boolean partitioned = networkService.isPartitioned();

        for (String id : nodeIds) {
            String cluster = networkService.getCluster(id);
            long skew = injectedSkews.getOrDefault(id, 0L);
            long rawLocal = System.currentTimeMillis() + skew;

            NodeClockSyncResult lastSync = lastSyncResults.get(id);
            boolean synced = (lastSync != null && lastSync.success());
            long rtt = synced ? lastSync.rttMillis() : 0L;
            long offset = synced ? lastSync.offsetMillis() : 0L;
            long corrected = synced ? (rawLocal + offset) : rawLocal;
            String syncTime = (lastSync != null) ? lastSync.lastSyncTime() : null;

            // Read vector clock snapshot (read-only from Grasit's package)
            Map<String, Integer> vc = Collections.emptyMap();
            try {
                if (vectorClockRegistry != null && vectorClockRegistry.contains(id)) {
                    vc = vectorClockRegistry.snapshot(id);
                }
            } catch (Exception e) {
                log.debug("Vector clock not yet available for {}: {}", id, e.getMessage());
            }

            String badge = partitioned ? "BLOCKED" : (synced ? "SYNCED" : "DRIFTING");

            list.add(new NodeClockStatus(
                    id, cluster, skew, rawLocal, rtt, offset,
                    corrected, synced, syncTime, vc, badge
            ));
        }

        List<String> steps = new ArrayList<>();
        steps.add("[Exp 3] Clock status evaluated for " + list.size() + " nodes.");
        if (partitioned) {
            steps.add("[Exp 3] Network partition is ACTIVE. Sync calls will be rejected.");
        } else {
            steps.add("[Exp 3] Network connectivity is NORMAL. Coordinator reachable.");
        }

        return new ClockStatusResponse(partitioned, list, steps);
    }

    public void setInjectedSkew(String nodeId, long skewMillis) {
        injectedSkews.put(nodeId, skewMillis);
        log.info("Set injected skew for {} to {} ms", nodeId, skewMillis);
    }

    public long getInjectedSkew(String nodeId) {
        return injectedSkews.getOrDefault(nodeId, 0L);
    }

    public List<String> getKnownNodeIds() {
        Set<String> set = new LinkedHashSet<>(List.of("UAV-01", "UAV-02", "Edge-01"));
        try {
            for (NodeEntity ne : nodeRepo.findAll()) {
                set.add(ne.getNodeId());
            }
        } catch (Exception ignored) { }
        return new ArrayList<>(set);
    }
}

