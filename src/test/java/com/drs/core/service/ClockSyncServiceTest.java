package com.drs.core.service;

import com.drs.consensus.reconcile.VectorClockRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClockSyncServiceTest {

    @Autowired private ClockSyncService clockSyncService;
    @Autowired private NetworkService networkService;
    @Autowired private VectorClockRegistry vectorClockRegistry;

    @BeforeEach
    void resetState() {
        networkService.heal();
        clockSyncService.setInjectedSkew("UAV-01", 1500L);
        clockSyncService.setInjectedSkew("UAV-02", -800L);
        clockSyncService.setInjectedSkew("Edge-01", 300L);
    }

    @Test
    @Order(1)
    @DisplayName("Exp 3: Cristian's algorithm calculates offset and corrects local drift")
    void testCristianSyncCalculation() {
        ClockSyncService.NodeClockSyncResult res = clockSyncService.syncNode("UAV-01");

        assertTrue(res.success());
        assertEquals("UAV-01", res.nodeId());
        assertEquals(1500L, res.injectedSkewMillis());
        assertTrue(res.rttMillis() >= 10, "RTT should include coordinator delay (10-60ms)");

        // Formula: offset = t1 + RTT/2 - t2
        long expectedOffset = res.serverTime() + (res.rttMillis() / 2) - res.t2();
        assertEquals(expectedOffset, res.offsetMillis());

        // Corrected time: t2 + offset = t1 + RTT/2
        assertEquals(res.t2() + res.offsetMillis(), res.correctedTime());
        assertNotNull(res.steps());
        assertTrue(res.steps().size() >= 5);
    }

    @Test
    @Order(2)
    @DisplayName("Exp 3: Negative skew is corrected properly")
    void testNegativeSkewSync() {
        ClockSyncService.NodeClockSyncResult res = clockSyncService.syncNode("UAV-02");

        assertTrue(res.success());
        assertEquals(-800L, res.injectedSkewMillis());
        assertTrue(res.offsetMillis() > 0, "Negative skew should result in positive offset");
    }

    @Test
    @Order(3)
    @DisplayName("Exp 3: Network partition blocks coordinator access during clock sync")
    void testNetworkPartitionBlocksSync() {
        // Partition network
        NetworkService.NetworkStatus partStatus = networkService.partition();
        assertTrue(partStatus.partitioned());
        assertFalse(networkService.canReachCoordinator("UAV-01"));

        // Attempt sync while partitioned
        ClockSyncService.NodeClockSyncResult res = clockSyncService.syncNode("UAV-01");
        assertFalse(res.success());
        assertNotNull(res.errorMessage());
        assertTrue(res.errorMessage().contains("UNREACHABLE"));

        // Heal network
        NetworkService.NetworkStatus healStatus = networkService.heal();
        assertFalse(healStatus.partitioned());
        assertTrue(networkService.canReachCoordinator("UAV-01"));

        // Sync now succeeds
        ClockSyncService.NodeClockSyncResult healedRes = clockSyncService.syncNode("UAV-01");
        assertTrue(healedRes.success());
    }

    @Test
    @Order(4)
    @DisplayName("Exp 3: Inter-cluster communication check during partition")
    void testInterClusterCommunication() {
        networkService.partition();
        // Intra-cluster (Cluster-A): UAV-01 and UAV-02
        assertTrue(networkService.canCommunicate("UAV-01", "UAV-02"));

        // Inter-cluster (Cluster-A to Cluster-B): UAV-01 and Edge-01
        assertFalse(networkService.canCommunicate("UAV-01", "Edge-01"));

        networkService.heal();
        // All communicate after heal
        assertTrue(networkService.canCommunicate("UAV-01", "Edge-01"));
    }

    @Test
    @Order(5)
    @DisplayName("Exp 3: Vector clock snapshot is reflected in clock status")
    void testVectorClockIntegration() {
        // Initialize vector clock in Grasit's registry
        vectorClockRegistry.init(List.of("UAV-01", "UAV-02", "Edge-01"));
        vectorClockRegistry.tick("UAV-01");

        ClockSyncService.ClockStatusResponse status = clockSyncService.getClockStatus();
        assertNotNull(status.nodes());

        ClockSyncService.NodeClockStatus uav1 = status.nodes().stream()
                .filter(n -> "UAV-01".equals(n.nodeId()))
                .findFirst()
                .orElseThrow();

        Map<String, Integer> vc = uav1.vectorClock();
        assertNotNull(vc);
        assertEquals(1, vc.get("UAV-01"));
    }

    @Test
    @Order(6)
    @DisplayName("Exp 3: syncAll synchronizes all registered nodes")
    void testSyncAll() {
        List<ClockSyncService.NodeClockSyncResult> all = clockSyncService.syncAll();
        assertTrue(all.size() >= 3);
        for (ClockSyncService.NodeClockSyncResult r : all) {
            assertTrue(r.success());
        }
    }
}

