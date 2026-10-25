package com.drs.consensus.model;

import java.util.*;

/**
 * A vector clock that keeps one logical counter per node.
 * <p>
 * Algorithm logic is identical to the legacy {@code common.VectorClock}.
 * Thread-safe via {@code synchronized} on mutable operations.
 */
public class VectorClock {

    private final Map<String, Integer> clock;

    public VectorClock(List<String> knownNodeIds) {
        this.clock = new TreeMap<>();
        for (String id : knownNodeIds) {
            clock.put(id, 0);
        }
    }

    /** Private constructor for snapshot copies. */
    private VectorClock(Map<String, Integer> existing) {
        this.clock = new TreeMap<>(existing);
    }

    public synchronized void tick(String nodeId) {
        clock.merge(nodeId, 1, Integer::sum);
    }

    public synchronized void mergeWith(Map<String, Integer> other) {
        for (Map.Entry<String, Integer> entry : other.entrySet()) {
            clock.merge(entry.getKey(), entry.getValue(), Math::max);
        }
    }

    public synchronized Map<String, Integer> snapshot() {
        return new TreeMap<>(clock);
    }

    /** Create a deep copy of this VectorClock. */
    public synchronized VectorClock copy() {
        return new VectorClock(this.clock);
    }

    public enum Relation { BEFORE, AFTER, CONCURRENT, EQUAL }

    /**
     * Compare two vector-clock snapshots.
     * Identical algorithm to legacy {@code common.VectorClock.compare}.
     */
    public static Relation compare(Map<String, Integer> a, Map<String, Integer> b) {
        boolean aLessOrEqualB = true;
        boolean bLessOrEqualA = true;
        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(a.keySet());
        allKeys.addAll(b.keySet());
        for (String key : allKeys) {
            int av = a.getOrDefault(key, 0);
            int bv = b.getOrDefault(key, 0);
            if (av > bv) aLessOrEqualB = false;
            if (bv > av) bLessOrEqualA = false;
        }
        if (aLessOrEqualB && bLessOrEqualA) return Relation.EQUAL;
        if (aLessOrEqualB) return Relation.BEFORE;
        if (bLessOrEqualA) return Relation.AFTER;
        return Relation.CONCURRENT;
    }

    @Override
    public synchronized String toString() {
        return clock.toString();
    }
}
