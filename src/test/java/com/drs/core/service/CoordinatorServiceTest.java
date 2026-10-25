package com.drs.core.service;

import com.drs.core.event.EventLog;
import com.drs.core.event.EventLogRepository;
import com.drs.core.model.NodeStatus;
import com.drs.core.model.NodeStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CoordinatorServiceTest {

    @Autowired CoordinatorService service;
    @Autowired NodeStatusRepository nodes;
    @Autowired EventLogRepository events;

    @BeforeEach
    void clean() {
        nodes.deleteAll();
        events.deleteAll();
    }

    // ── Exp 1 ──

    @Test
    void registerComputesScoreAndStatePersistsAndRunsOnWorkerPool() {
        var r = service.registerNode("UAV-01", 80, 70, "UAV", "Cluster-A");

        assertEquals(220, r.connectivityScore());
        assertEquals("STABLE", r.state());
        assertTrue(r.thread().startsWith("pool-"), "should run on the executor thread, was " + r.thread());
        assertEquals("STABLE", service.getNodeState("UAV-01"));
        assertEquals(1, nodes.count());
    }

    @Test
    void registerPublishesNodeRegisteredEvent() {
        service.registerNode("UAV-02", 60, 55, "UAV", "Cluster-A");

        List<EventLog> log = events.findAll();
        assertTrue(log.stream().anyMatch(e -> e.getModule().equals("coordinator")
                && e.getType().equals("NODE_REGISTERED")
                && e.getPayload().contains("UAV-02")));
    }

    @Test
    void reRegisteringUpdatesInsteadOfDuplicating() {
        service.registerNode("Edge-01", 10, 10, "EDGE", "Cluster-B");
        assertEquals("PARTITIONED", service.getNodeState("Edge-01"));

        service.registerNode("Edge-01", 90, 85, "EDGE", "Cluster-B");
        assertEquals("STABLE", service.getNodeState("Edge-01"));
        assertEquals(1, nodes.count());
    }

    @Test
    void unknownNodeHasNoState() {
        assertNull(service.getNodeState("ghost"));
    }

    // ── Exp 2 ──

    @Test
    void heartbeatUpdatesLastHeartbeatForRegisteredNode() {
        service.registerNode("UAV-01", 80, 70, "UAV", "Cluster-A");
        long ts = System.currentTimeMillis() + 5000;

        var hb = service.sendHeartbeat("UAV-01", ts);

        assertEquals(ts, hb.timestamp());
        assertEquals(ts, nodes.findById("UAV-01").orElseThrow().getLastHeartbeat().toEpochMilli());
    }

    @Test
    void heartbeatFromUnknownNodeWarnsButDoesNotCreateIt() {
        var hb = service.sendHeartbeat("ghost");

        assertTrue(hb.steps().stream().anyMatch(s -> s.contains("not registered")));
        assertEquals(0, nodes.count());
    }

    @Test
    void silentNodeIsFlaggedOnlyOncePerSilenceEpisode() {
        service.registerNode("UAV-01", 80, 70, "UAV", "Cluster-A");
        makeSilent("UAV-01", 7000);

        var first = service.checkForSilentNodes();
        var second = service.checkForSilentNodes();

        assertEquals(1, first.size());
        assertEquals("UAV-01", first.get(0).nodeId());
        assertTrue(first.get(0).silentForMs() > 6000);
        assertEquals(6000, first.get(0).thresholdMs());
        assertTrue(second.isEmpty(), "must not re-alert every second");
    }

    @Test
    void nodeJustUnderThresholdIsNotFlagged() {
        service.registerNode("UAV-01", 80, 70, "UAV", "Cluster-A");
        makeSilent("UAV-01", 3000);

        assertTrue(service.checkForSilentNodes().isEmpty());
    }

    @Test
    void recoveredNodeCanBeFlaggedAgainLater() {
        service.registerNode("UAV-01", 80, 70, "UAV", "Cluster-A");
        makeSilent("UAV-01", 7000);
        assertEquals(1, service.checkForSilentNodes().size());

        service.sendHeartbeat("UAV-01");                 // node recovers
        assertTrue(service.checkForSilentNodes().isEmpty());

        makeSilent("UAV-01", 7000);                      // goes quiet again
        assertEquals(1, service.checkForSilentNodes().size());
    }

    // ── Exp 3 ──

    @Test
    void getTimeReturnsServerClockAfterSmallDelay() {
        long before = System.currentTimeMillis();
        var t = service.getTime();
        long after = System.currentTimeMillis();

        assertTrue(t.serverTime() >= before && t.serverTime() <= after);
        assertTrue(after - before >= 10, "legacy server sleeps 10-60 ms before answering");
    }

    private void makeSilent(String nodeId, long agoMs) {
        NodeStatus n = nodes.findById(nodeId).orElseThrow();
        n.setLastHeartbeat(Instant.now().minusMillis(agoMs));
        nodes.save(n);
    }
}
