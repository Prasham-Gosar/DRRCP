package com.drs.consensus.reconcile;

import com.drs.consensus.model.*;
import com.drs.core.event.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ReconciliationService}.
 * Covers the three scenarios from the README:
 * <ol>
 *   <li>House-12 concurrent RESCUED vs NEEDS_RESCUE → safety-first → NEEDS_RESCUE wins, needsHumanReview=true</li>
 *   <li>Two different survivor names → CRDT union, no conflict</li>
 *   <li>Causally ordered updates → later one wins, no conflict</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    EventPublisher eventPublisher;

    ReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(eventPublisher);
    }

    // ── Helpers ──

    /** Build a vector clock snapshot: {UAV-01: a, UAV-02: b, Edge-01: c}. */
    private Map<String, Integer> vc(int a, int b, int c) {
        Map<String, Integer> m = new TreeMap<>();
        m.put("UAV-01", a);
        m.put("UAV-02", b);
        m.put("Edge-01", c);
        return m;
    }

    // ── Test 1: concurrent RESCUED vs NEEDS_RESCUE ──

    @Test
    @DisplayName("House-12: concurrent RESCUED vs NEEDS_RESCUE → NEEDS_RESCUE wins, needsHumanReview=true")
    void concurrentStatusConflict() {
        // UAV-01 in Cluster-A marks House-12 as RESCUED
        UpdateRecord rescued = new UpdateRecord(
                "REC-1", "House-12", "STATUS", "RESCUED",
                "Cluster-A", "UAV-01", "UAV-01 marks House-12 rescued",
                vc(2, 0, 0)  // only UAV-01 has ticked
        );

        // Edge-01 in Cluster-B marks House-12 as NEEDS_RESCUE (concurrent — different dimension ticked)
        UpdateRecord needsRescue = new UpdateRecord(
                "REC-2", "House-12", "STATUS", "NEEDS_RESCUE",
                "Cluster-B", "Edge-01", "Edge-01 marks House-12 needs rescue",
                vc(0, 0, 2)  // only Edge-01 has ticked
        );

        // Verify the vectors are genuinely concurrent
        assertEquals(VectorClock.Relation.CONCURRENT,
                VectorClock.compare(rescued.getVector(), needsRescue.getVector()));

        ReconciliationResult result = service.reconcile(List.of(rescued, needsRescue));

        assertEquals(1, result.getResolutions().size());
        Resolution res = result.getResolutions().get(0);

        assertEquals("House-12", res.getResourceKey());
        assertEquals("STATUS", res.getResourceType());
        assertEquals("NEEDS_RESCUE", res.getFinalValue(), "Safety-first: NEEDS_RESCUE must win");
        assertEquals("POLICY_RESOLVED", res.getMethod());
        assertTrue(res.isNeedsHumanReview(), "Concurrent conflict must be flagged for human review");
        assertEquals(1, result.getConflictsDetected());
        assertEquals(1, result.getHumanReviewRequired());
    }

    // ── Test 2: two survivor names → CRDT union ──

    @Test
    @DisplayName("Two different survivor names are unioned with no conflict (CRDT SET_ADD)")
    void survivorNamesCrdtUnion() {
        UpdateRecord survivor1 = new UpdateRecord(
                "REC-3", "confirmedSurvivors", "SET_ADD", "Alice",
                "Cluster-A", "UAV-01", "Found survivor Alice",
                vc(1, 0, 0)
        );
        UpdateRecord survivor2 = new UpdateRecord(
                "REC-4", "confirmedSurvivors", "SET_ADD", "Bob",
                "Cluster-B", "Edge-01", "Found survivor Bob",
                vc(0, 0, 1)
        );

        ReconciliationResult result = service.reconcile(List.of(survivor1, survivor2));

        assertEquals(1, result.getResolutions().size());
        Resolution res = result.getResolutions().get(0);

        assertEquals("confirmedSurvivors", res.getResourceKey());
        assertEquals("SET_ADD", res.getResourceType());
        assertEquals("CRDT_MERGED", res.getMethod());
        assertFalse(res.isNeedsHumanReview(), "CRDT union should never need human review");
        // finalValue is the set's toString: "[Alice, Bob]"
        assertTrue(res.getFinalValue().contains("Alice"));
        assertTrue(res.getFinalValue().contains("Bob"));
        assertEquals(0, result.getConflictsDetected());
    }

    // ── Test 3: causally ordered → no conflict ──

    @Test
    @DisplayName("Causally ordered STATUS updates → later one wins, no human review")
    void causallyOrderedNoConflict() {
        UpdateRecord earlier = new UpdateRecord(
                "REC-5", "House-99", "STATUS", "UNKNOWN",
                "Cluster-A", "UAV-01", "Initial report",
                vc(1, 0, 0)
        );
        UpdateRecord later = new UpdateRecord(
                "REC-6", "House-99", "STATUS", "RESCUED",
                "Cluster-A", "UAV-02", "Confirmed rescued",
                vc(2, 1, 0)  // happened-after: dominates (1,0,0)
        );

        assertEquals(VectorClock.Relation.BEFORE,
                VectorClock.compare(earlier.getVector(), later.getVector()));

        ReconciliationResult result = service.reconcile(List.of(earlier, later));

        assertEquals(1, result.getResolutions().size());
        Resolution res = result.getResolutions().get(0);

        assertEquals("RESCUED", res.getFinalValue());
        assertEquals("CAUSALLY_RESOLVED", res.getMethod());
        assertFalse(res.isNeedsHumanReview());
        assertEquals(0, result.getConflictsDetected());
    }

    // ── Test 4: single update → SINGLE_UPDATE ──

    @Test
    @DisplayName("Single update → SINGLE_UPDATE, no conflict")
    void singleUpdate() {
        UpdateRecord only = new UpdateRecord(
                "REC-7", "Bridge-02", "STATUS", "BLOCKED",
                "Cluster-A", "UAV-01", "Bridge blocked",
                vc(1, 0, 0)
        );

        ReconciliationResult result = service.reconcile(List.of(only));

        Resolution res = result.getResolutions().get(0);
        assertEquals("BLOCKED", res.getFinalValue());
        assertEquals("SINGLE_UPDATE", res.getMethod());
        assertFalse(res.isNeedsHumanReview());
    }

    // ── Test 5: pending update store ──

    @Test
    @DisplayName("addUpdate/getUpdates/clearUpdates lifecycle")
    void updateStoreLifecycle() {
        assertTrue(service.getUpdates().isEmpty());

        UpdateRecord u = new UpdateRecord(
                "REC-8", "Shelter-01", "STATUS", "OPEN",
                "Cluster-A", "UAV-01", "Shelter open",
                vc(1, 0, 0)
        );
        service.addUpdate(u);
        assertEquals(1, service.getUpdates().size());

        service.clearUpdates();
        assertTrue(service.getUpdates().isEmpty());
    }

    // ── Test 6: reconcile with null uses the pending store ──

    @Test
    @DisplayName("reconcile(null) reconciles the internal pending store")
    void reconcileFromPendingStore() {
        service.addUpdate(new UpdateRecord(
                "REC-9", "House-50", "STATUS", "NEEDS_RESCUE",
                "Cluster-A", "UAV-01", "Needs rescue",
                vc(1, 0, 0)
        ));

        ReconciliationResult result = service.reconcile(null);
        assertEquals(1, result.getResolutions().size());
        assertEquals("NEEDS_RESCUE", result.getResolutions().get(0).getFinalValue());
    }

    // ── Test 7: mixed resources ──

    @Test
    @DisplayName("Multiple resources reconciled together")
    void multipleResources() {
        List<UpdateRecord> updates = List.of(
                new UpdateRecord("R1", "House-12", "STATUS", "RESCUED", "A", "UAV-01", "d1", vc(2, 0, 0)),
                new UpdateRecord("R2", "House-12", "STATUS", "NEEDS_RESCUE", "B", "Edge-01", "d2", vc(0, 0, 2)),
                new UpdateRecord("R3", "survivors", "SET_ADD", "Alice", "A", "UAV-01", "d3", vc(1, 0, 0)),
                new UpdateRecord("R4", "survivors", "SET_ADD", "Bob", "B", "Edge-01", "d4", vc(0, 0, 1))
        );

        ReconciliationResult result = service.reconcile(updates);
        assertEquals(2, result.getResolutions().size());
        assertEquals(4, result.getTotalUpdates());
        // House-12 is POLICY_RESOLVED, survivors is CRDT_MERGED
        assertEquals(1, result.getConflictsDetected());
    }
}
