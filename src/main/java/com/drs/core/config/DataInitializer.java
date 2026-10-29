package com.drs.core.config;

import com.drs.consensus.reconcile.VectorClockRegistry;
import com.drs.core.event.EventPublisher;
import com.drs.core.model.NodeEntity;
import com.drs.core.model.NodeEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Seeds demo UAV/Edge nodes, vector clocks, and initial telemetry on application startup
 * so the dashboard and tables are immediately interactive on first launch.
 * <p>
 * Can be disabled in tests via {@code drs.seed.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "drs.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final NodeEntityRepository nodeRepo;
    private final VectorClockRegistry vectorClockRegistry;
    private final EventPublisher eventPublisher;

    public DataInitializer(NodeEntityRepository nodeRepo,
                           VectorClockRegistry vectorClockRegistry,
                           EventPublisher eventPublisher) {
        this.nodeRepo = nodeRepo;
        this.vectorClockRegistry = vectorClockRegistry;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (nodeRepo.count() > 0) {
            log.info("[DRS-Bootstrap] Node database already populated ({} nodes found).", nodeRepo.count());
            return;
        }

        log.info("[DRS-Bootstrap] Seeding initial disaster relief UAV and edge nodes...");

        // 1. UAV-01 (Cluster-A)
        NodeEntity uav1 = new NodeEntity("UAV-01", 85, 75, "UAV", "Cluster-A");
        uav1.setLastThreadName("heartbeat-pool-1");
        uav1.setStatus("ALIVE");
        uav1.setLastHeartbeat(Instant.now());
        nodeRepo.save(uav1);

        // 2. UAV-02 (Cluster-A)
        NodeEntity uav2 = new NodeEntity("UAV-02", 60, 50, "UAV", "Cluster-A");
        uav2.setLastThreadName("heartbeat-pool-2");
        uav2.setStatus("ALIVE");
        uav2.setLastHeartbeat(Instant.now());
        nodeRepo.save(uav2);

        // 3. Edge-01 (Cluster-B)
        NodeEntity edge1 = new NodeEntity("Edge-01", 95, 90, "EDGE", "Cluster-B");
        edge1.setLastThreadName("heartbeat-pool-3");
        edge1.setStatus("ALIVE");
        edge1.setLastHeartbeat(Instant.now());
        nodeRepo.save(edge1);

        // 4. Initialize vector clocks for nodes
        try {
            vectorClockRegistry.init(List.of("UAV-01", "UAV-02", "Edge-01"));
        } catch (Exception e) {
            log.debug("Vector clock init skipped: {}", e.getMessage());
        }

        // 5. Publish initial welcome events to SSE bus
        eventPublisher.publish("core", "NODE_REGISTERED", Map.of(
                "nodeId", "UAV-01", "state", "STABLE", "score", 235, "clusterId", "Cluster-A"
        ));
        eventPublisher.publish("core", "NODE_REGISTERED", Map.of(
                "nodeId", "UAV-02", "state", "STABLE", "score", 160, "clusterId", "Cluster-A"
        ));
        eventPublisher.publish("core", "NODE_REGISTERED", Map.of(
                "nodeId", "Edge-01", "state", "STABLE", "score", 275, "clusterId", "Cluster-B"
        ));

        log.info("[DRS-Bootstrap] Seeded 3 active nodes (UAV-01, UAV-02, Edge-01) with initial telemetry.");
    }
}
