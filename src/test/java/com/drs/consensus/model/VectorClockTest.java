package com.drs.consensus.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link VectorClock} — the core data structure shared by Exp 3 and 5.
 */
class VectorClockTest {

    @Test
    @DisplayName("New clock starts at all zeroes")
    void initiallyZero() {
        VectorClock vc = new VectorClock(List.of("A", "B", "C"));
        Map<String, Integer> snap = vc.snapshot();
        assertEquals(0, snap.get("A"));
        assertEquals(0, snap.get("B"));
        assertEquals(0, snap.get("C"));
    }

    @Test
    @DisplayName("tick increments the correct entry")
    void tickIncrements() {
        VectorClock vc = new VectorClock(List.of("A", "B"));
        vc.tick("A");
        vc.tick("A");
        vc.tick("B");
        Map<String, Integer> snap = vc.snapshot();
        assertEquals(2, snap.get("A"));
        assertEquals(1, snap.get("B"));
    }

    @Test
    @DisplayName("mergeWith takes component-wise max")
    void mergeMax() {
        VectorClock vc = new VectorClock(List.of("A", "B"));
        vc.tick("A");  // {A:1, B:0}
        vc.mergeWith(Map.of("A", 0, "B", 5));  // should become {A:1, B:5}
        Map<String, Integer> snap = vc.snapshot();
        assertEquals(1, snap.get("A"));
        assertEquals(5, snap.get("B"));
    }

    @Test
    @DisplayName("compare: EQUAL when both are identical")
    void compareEqual() {
        assertEquals(VectorClock.Relation.EQUAL,
                VectorClock.compare(Map.of("A", 1, "B", 2), Map.of("A", 1, "B", 2)));
    }

    @Test
    @DisplayName("compare: BEFORE when a <= b component-wise and a != b")
    void compareBefore() {
        assertEquals(VectorClock.Relation.BEFORE,
                VectorClock.compare(Map.of("A", 1, "B", 0), Map.of("A", 2, "B", 1)));
    }

    @Test
    @DisplayName("compare: AFTER when a >= b component-wise and a != b")
    void compareAfter() {
        assertEquals(VectorClock.Relation.AFTER,
                VectorClock.compare(Map.of("A", 2, "B", 1), Map.of("A", 1, "B", 0)));
    }

    @Test
    @DisplayName("compare: CONCURRENT when neither dominates the other")
    void compareConcurrent() {
        assertEquals(VectorClock.Relation.CONCURRENT,
                VectorClock.compare(Map.of("A", 2, "B", 0), Map.of("A", 0, "B", 2)));
    }

    @Test
    @DisplayName("compare handles missing keys (defaulting to 0)")
    void compareMissingKeys() {
        // {A:1} vs {B:1} — concurrent, because A>0 in first, B>0 in second
        assertEquals(VectorClock.Relation.CONCURRENT,
                VectorClock.compare(Map.of("A", 1), Map.of("B", 1)));
    }

    @Test
    @DisplayName("snapshot returns a defensive copy")
    void snapshotIsCopy() {
        VectorClock vc = new VectorClock(List.of("A"));
        Map<String, Integer> snap = vc.snapshot();
        snap.put("A", 999);
        assertEquals(0, vc.snapshot().get("A"), "Mutating snapshot must not affect internal state");
    }

    @Test
    @DisplayName("copy returns an independent VectorClock")
    void copyIsIndependent() {
        VectorClock vc = new VectorClock(List.of("A", "B"));
        vc.tick("A");
        VectorClock clone = vc.copy();
        clone.tick("B");
        assertEquals(0, vc.snapshot().get("B"), "Original must be unaffected by copy's tick");
        assertEquals(1, clone.snapshot().get("B"));
    }
}
