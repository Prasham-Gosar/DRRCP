package com.drs.consensus.reconcile;

import com.drs.consensus.model.VectorClock;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that keeps one {@link VectorClock} per node id.
 * <p>
 * Used by reconciliation (Exp 5) and by Prasham's core services (Exp 3)
 * to tick / snapshot / merge vector clocks.
 */
@Service
public class VectorClockRegistry {

    /** nodeId → VectorClock. */
    private final ConcurrentHashMap<String, VectorClock> clocks = new ConcurrentHashMap<>();

    /**
     * Initialise (or re-initialise) clocks for a known set of node ids.
     * Existing clocks for those nodes are replaced.
     */
    public void init(List<String> nodeIds) {
        for (String id : nodeIds) {
            clocks.put(id, new VectorClock(nodeIds));
        }
    }

    /** Ensure clock exists for the given node, registering default known nodes if missing. */
    public void ensureRegistered(String nodeId, List<String> defaultKnownNodes) {
        clocks.computeIfAbsent(nodeId, id -> {
            List<String> nodes = (defaultKnownNodes != null && !defaultKnownNodes.isEmpty())
                    ? defaultKnownNodes : List.of("UAV-01", "UAV-02", "Edge-01", "Edge-02", "Base-01");
            if (!nodes.contains(id)) {
                nodes = new java.util.ArrayList<>(nodes);
                nodes.add(id);
            }
            return new VectorClock(nodes);
        });
    }

    /**
     * Tick the vector clock for the given node.
     *
     * @throws IllegalStateException if the node has no clock registered
     */
    public void tick(String nodeId) {
        VectorClock vc = getOrThrow(nodeId);
        vc.tick(nodeId);
    }

    /** Get a snapshot of the given node's vector clock. */
    public Map<String, Integer> snapshot(String nodeId) {
        return getOrThrow(nodeId).snapshot();
    }

    /** Merge another clock snapshot into this node's clock. */
    public void merge(String nodeId, Map<String, Integer> other) {
        getOrThrow(nodeId).mergeWith(other);
    }

    /** Check whether a clock exists for the given node. */
    public boolean contains(String nodeId) {
        return clocks.containsKey(nodeId);
    }

    /** Remove all registered clocks. */
    public void clear() {
        clocks.clear();
    }

    /** Return all registered node ids. */
    public java.util.Set<String> registeredNodes() {
        return java.util.Collections.unmodifiableSet(clocks.keySet());
    }

    private VectorClock getOrThrow(String nodeId) {
        VectorClock vc = clocks.get(nodeId);
        if (vc == null) {
            throw new IllegalStateException("No vector clock registered for node: " + nodeId);
        }
        return vc;
    }
}
