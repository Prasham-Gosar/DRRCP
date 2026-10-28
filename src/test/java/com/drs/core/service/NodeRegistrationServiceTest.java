package com.drs.core.service;

import com.drs.core.model.NodeEntity;
import com.drs.core.model.NodeEntityRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NodeRegistrationServiceTest {

    @Autowired private NodeRegistrationService service;
    @Autowired private NodeEntityRepository repo;
    @Autowired private SilentNodeMonitor monitor;

    @BeforeEach
    void cleanDb() {
        repo.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("Exp1: score = 2×signal + battery (high values → STABLE)")
    void testConnectivityScore_stable() {
        var result = service.registerNode("UAV-01", 85, 75, "UAV", "Cluster-A");

        assertEquals("UAV-01", result.nodeId());
        assertEquals(2 * 75 + 85, result.connectivityScore());
        assertEquals("STABLE", result.state());
        assertNotNull(result.thread());
        assertTrue(result.thread().startsWith("heartbeat-pool-"));
        assertTrue(result.steps().size() >= 3);
    }

    @Test
    @Order(2)
    @DisplayName("Exp1: low signal+battery → AT_RISK / PARTITION_LIKELY")
    void testConnectivityScore_atRisk() {
        var atRisk = service.registerNode("Edge-01", 30, 40, "EDGE", "Cluster-B");
        assertEquals(110, atRisk.connectivityScore());
        assertEquals("AT_RISK", atRisk.state());

        var partLikely = service.registerNode("Edge-02", 15, 20, "EDGE", "Cluster-C");
        assertEquals(55, partLikely.connectivityScore());
        assertEquals("PARTITION_LIKELY", partLikely.state());
    }

    @Test
    @Order(3)
    @DisplayName("Exp1: very low values → PARTITIONED")
    void testConnectivityScore_partitioned() {
        var result = service.registerNode("UAV-99", 5, 10, "UAV", "Cluster-X");
        assertEquals(25, result.connectivityScore());
        assertEquals("PARTITIONED", result.state());
    }

    @Test
    @Order(4)
    @DisplayName("Exp1: boundary score 150 → STABLE")
    void testConnectivityScore_boundary() {
        var result = service.registerNode("Boundary-01", 50, 50, "UAV", "Cluster-A");
        assertEquals(150, result.connectivityScore());
        assertEquals("STABLE", result.state());
    }

    @Test
    @Order(5)
    @DisplayName("Exp1: re-registration updates existing node")
    void testReRegistration() {
        service.registerNode("UAV-01", 85, 75, "UAV", "Cluster-A");
        var updated = service.registerNode("UAV-01", 30, 20, "UAV", "Cluster-A");
        assertEquals(2 * 20 + 30, updated.connectivityScore());
        assertEquals("PARTITION_LIKELY", updated.state());
        assertEquals(1, repo.count());
    }

    @Test
    @Order(10)
    @DisplayName("Exp2: heartbeat returns pool thread name")
    void testHeartbeatReturnsThread() {
        service.registerNode("UAV-01", 85, 75, "UAV", "Cluster-A");

        var hb = service.sendHeartbeat("UAV-01");
        assertNotNull(hb.thread());
        assertTrue(hb.thread().startsWith("heartbeat-pool-"));
        assertEquals("UAV-01", hb.nodeId());
    }

    @Test
    @Order(11)
    @DisplayName("Exp2: heartbeat for unregistered node warns")
    void testHeartbeatUnregistered() {
        var hb = service.sendHeartbeat("GHOST-01");
        assertTrue(hb.steps().stream().anyMatch(s -> s.contains("WARNING")));
    }

    @Test
    @Order(12)
    @DisplayName("Exp2: heartbeat updates lastHeartbeat and lastThreadName")
    void testHeartbeatUpdatesEntity() {
        service.registerNode("Edge-01", 95, 90, "EDGE", "Cluster-B");
        long ts = System.currentTimeMillis();
        service.sendHeartbeat("Edge-01", ts);

        NodeEntity entity = repo.findById("Edge-01").orElseThrow();
        assertEquals(Instant.ofEpochMilli(ts), entity.getLastHeartbeat());
        assertNotNull(entity.getLastThreadName());
        assertEquals("ALIVE", entity.getStatus());
    }

    @Test
    @Order(20)
    @DisplayName("Exp2: node with old heartbeat is detected as SILENT")
    void testSilentDetection() {
        service.registerNode("UAV-01", 85, 75, "UAV", "Cluster-A");
        NodeEntity entity = repo.findById("UAV-01").orElseThrow();
        entity.setLastHeartbeat(Instant.ofEpochMilli(System.currentTimeMillis() - 10_000));
        repo.save(entity);

        long originalThreshold = monitor.getThresholdMs();
        monitor.setThresholdMs(5000);
        try {
            List<SilentNodeMonitor.SilentNodeAlert> alerts = monitor.checkForSilentNodes();
            assertEquals(1, alerts.size());
            assertEquals("UAV-01", alerts.get(0).nodeId());

            NodeEntity updated = repo.findById("UAV-01").orElseThrow();
            assertEquals("SILENT", updated.getStatus());
        } finally {
            monitor.setThresholdMs(originalThreshold);
        }
    }

    @Test
    @Order(21)
    @DisplayName("Exp2: fresh heartbeat node is NOT detected as silent")
    void testNotSilent() {
        service.registerNode("UAV-01", 85, 75, "UAV", "Cluster-A");
        List<SilentNodeMonitor.SilentNodeAlert> alerts = monitor.checkForSilentNodes();
        assertEquals(0, alerts.size());
    }

    @Test
    @Order(22)
    @DisplayName("Exp2: alert fires only once per silence episode")
    void testAlertOncePerEpisode() {
        service.registerNode("UAV-02", 60, 50, "UAV", "Cluster-A");
        NodeEntity entity = repo.findById("UAV-02").orElseThrow();
        entity.setLastHeartbeat(Instant.ofEpochMilli(System.currentTimeMillis() - 10_000));
        repo.save(entity);

        monitor.setThresholdMs(5000);
        try {
            List<SilentNodeMonitor.SilentNodeAlert> first = monitor.checkForSilentNodes();
            assertEquals(1, first.size());

            List<SilentNodeMonitor.SilentNodeAlert> second = monitor.checkForSilentNodes();
            assertEquals(0, second.size());

            monitor.clearAlert("UAV-02");
            List<SilentNodeMonitor.SilentNodeAlert> third = monitor.checkForSilentNodes();
            assertEquals(1, third.size());
        } finally {
            monitor.setThresholdMs(6000);
        }
    }

    @Test
    @Order(23)
    @DisplayName("Exp2: heartbeat revives a silent node to ALIVE")
    void testHeartbeatRevivesSilentNode() {
        service.registerNode("UAV-01", 85, 75, "UAV", "Cluster-A");
        NodeEntity entity = repo.findById("UAV-01").orElseThrow();
        entity.setStatus("SILENT");
        entity.setLastHeartbeat(Instant.ofEpochMilli(System.currentTimeMillis() - 10_000));
        repo.save(entity);

        service.sendHeartbeat("UAV-01");

        NodeEntity revived = repo.findById("UAV-01").orElseThrow();
        assertEquals("ALIVE", revived.getStatus());
    }

    @Test
    @Order(30)
    @DisplayName("getAllNodes returns all registered nodes")
    void testGetAllNodes() {
        service.registerNode("UAV-01", 85, 75, "UAV", "Cluster-A");
        service.registerNode("UAV-02", 60, 50, "UAV", "Cluster-A");
        service.registerNode("Edge-01", 95, 90, "EDGE", "Cluster-B");

        List<NodeEntity> all = service.getAllNodes();
        assertEquals(3, all.size());
    }

    @Test
    @Order(31)
    @DisplayName("getNode returns specific node or empty")
    void testGetNodeById() {
        service.registerNode("UAV-01", 85, 75, "UAV", "Cluster-A");

        assertTrue(service.getNode("UAV-01").isPresent());
        assertTrue(service.getNode("NONEXISTENT").isEmpty());
    }
}