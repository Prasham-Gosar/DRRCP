package com.drs.consensus.reconcile;

import com.drs.consensus.model.*;
import com.drs.core.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reconciliation Engine — Spring service port of legacy {@code common.ReconciliationEngine}.
 * <p>
 * Algorithm logic (causal frontier, safety-first status precedence, CRDT set union)
 * is identical to the original. {@code System.out.println} replaced by {@link ReconciliationResult#getSteps()}.
 * <p>
 * Also acts as an in-memory store for pending {@link UpdateRecord}s submitted
 * during a network partition.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    // Safety-first priority: lower index = higher priority ("more dangerous")
    private static final List<String> STATUS_PRIORITY = List.of(
            "NEEDS_RESCUE", "UNKNOWN", "RESCUED"
    );

    private final EventPublisher eventPublisher;

    /** Thread-safe store of pending updates. */
    private final CopyOnWriteArrayList<UpdateRecord> pendingUpdates = new CopyOnWriteArrayList<>();

    public ReconciliationService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    // ── update management ──

    /** Add an update to the pending store (called during partition). */
    public void addUpdate(UpdateRecord update) {
        pendingUpdates.add(update);
        log.debug("Added update {}", update.getRecordId());
    }

    /** Return all pending updates (read-only copy). */
    public List<UpdateRecord> getUpdates() {
        return List.copyOf(pendingUpdates);
    }

    /** Clear all pending updates (typically after reconciliation or reset). */
    public void clearUpdates() {
        pendingUpdates.clear();
    }

    // ── reconcile (core algorithm, unchanged from legacy) ──

    /**
     * Reconcile the given updates. If {@code null}, reconciles the internal pending store.
     *
     * @param updates the updates to reconcile, or null to use the pending store
     * @return structured result with resolutions, steps, and summary stats
     */
    public ReconciliationResult reconcile(List<UpdateRecord> updates) {
        List<UpdateRecord> toReconcile = (updates != null) ? updates : List.copyOf(pendingUpdates);
        List<String> steps = new ArrayList<>();
        steps.add("Starting reconciliation of " + toReconcile.size() + " updates.");

        // Group by resource key
        Map<String, List<UpdateRecord>> byResource = new LinkedHashMap<>();
        for (UpdateRecord r : toReconcile) {
            byResource.computeIfAbsent(r.getResourceKey(), k -> new ArrayList<>()).add(r);
        }
        steps.add("Found " + byResource.size() + " distinct resource(s).");

        List<Resolution> resolutions = new ArrayList<>();
        int conflictsDetected = 0;
        int humanReview = 0;

        for (Map.Entry<String, List<UpdateRecord>> entry : byResource.entrySet()) {
            String resourceKey = entry.getKey();
            List<UpdateRecord> resourceUpdates = entry.getValue();
            String type = resourceUpdates.get(0).getResourceType();

            Resolution res;
            if ("SET_ADD".equals(type)) {
                res = mergeSet(resourceKey, resourceUpdates, steps);
            } else {
                res = resolveStatus(resourceKey, resourceUpdates, steps);
            }
            resolutions.add(res);
            if (res.isNeedsHumanReview()) {
                conflictsDetected++;
                humanReview++;
            }
        }

        steps.add("Reconciliation complete: " + resolutions.size() + " resource(s) resolved, "
                + conflictsDetected + " conflict(s), " + humanReview + " flagged for human review.");

        ReconciliationResult result = new ReconciliationResult(
                resolutions, steps, toReconcile.size(), conflictsDetected, humanReview);

        log.info("Reconciliation done: {} updates, {} resolutions, {} conflicts",
                toReconcile.size(), resolutions.size(), conflictsDetected);
        eventPublisher.publish("consensus", "RECONCILED", result);

        return result;
    }

    // ── CRDT set merge (unchanged from legacy) ──

    private Resolution mergeSet(String resourceKey, List<UpdateRecord> updates, List<String> steps) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (UpdateRecord r : updates) merged.add(r.getValue());

        steps.add("[" + resourceKey + "] SET_ADD: " + updates.size()
                + " element(s) unioned -> " + merged);

        Resolution res = new Resolution();
        res.setResourceKey(resourceKey);
        res.setResourceType("SET_ADD");
        res.setFinalValue(merged.toString());
        res.setMethod("CRDT_MERGED");
        res.setNeedsHumanReview(false);
        res.setContributingUpdates(toMapList(updates));
        res.setExplanation("Grow-only set: all " + updates.size()
                + " concurrently-added elements are unioned automatically, no policy needed.");
        return res;
    }

    // ── status resolution with causal frontier (unchanged from legacy) ──

    private Resolution resolveStatus(String resourceKey, List<UpdateRecord> updates, List<String> steps) {
        List<UpdateRecord> frontier = causalFrontier(updates);

        Resolution res = new Resolution();
        res.setResourceKey(resourceKey);
        res.setResourceType("STATUS");
        res.setContributingUpdates(toMapList(frontier));

        if (frontier.size() == 1) {
            UpdateRecord winner = frontier.get(0);
            res.setFinalValue(winner.getValue());
            res.setMethod(updates.size() == 1 ? "SINGLE_UPDATE" : "CAUSALLY_RESOLVED");
            res.setNeedsHumanReview(false);
            res.setExplanation(updates.size() == 1
                    ? "Only one update was ever made to this resource."
                    : "One update causally follows all others, so it safely wins with no conflict.");
            steps.add("[" + resourceKey + "] STATUS: " + res.getMethod()
                    + " -> " + res.getFinalValue());
            return res;
        }

        // Multiple concurrent, conflicting updates — safety-first policy
        UpdateRecord chosen = frontier.get(0);
        for (UpdateRecord r : frontier) {
            if (priorityIndex(r.getValue()) < priorityIndex(chosen.getValue())) {
                chosen = r;
            }
        }
        res.setFinalValue(chosen.getValue());
        res.setMethod("POLICY_RESOLVED");
        res.setNeedsHumanReview(true);
        res.setExplanation(frontier.size() + " concurrent, conflicting updates were found. "
                + "Safety-first policy applied: '" + chosen.getValue() + "' takes precedence "
                + "because a possible distress signal must never be silently discarded. "
                + "Flagged for human re-verification.");
        steps.add("[" + resourceKey + "] STATUS: CONFLICT (" + frontier.size()
                + " concurrent) -> POLICY_RESOLVED to '" + chosen.getValue()
                + "', needsHumanReview=true");
        return res;
    }

    /** Find the causal frontier: updates not causally dominated by any other. */
    private List<UpdateRecord> causalFrontier(List<UpdateRecord> updates) {
        List<UpdateRecord> frontier = new ArrayList<>();
        for (UpdateRecord candidate : updates) {
            boolean dominated = false;
            for (UpdateRecord other : updates) {
                if (candidate == other) continue;
                VectorClock.Relation rel = VectorClock.compare(candidate.getVector(), other.getVector());
                if (rel == VectorClock.Relation.BEFORE) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) frontier.add(candidate);
        }
        return frontier;
    }

    private int priorityIndex(String value) {
        int idx = STATUS_PRIORITY.indexOf(value);
        return idx == -1 ? Integer.MAX_VALUE : idx;
    }

    /** Convert UpdateRecords to serialisable maps for the Resolution DTO. */
    private List<Map<String, Object>> toMapList(List<UpdateRecord> records) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (UpdateRecord r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recordId", r.getRecordId());
            m.put("resourceKey", r.getResourceKey());
            m.put("resourceType", r.getResourceType());
            m.put("value", r.getValue());
            m.put("clusterId", r.getClusterId());
            m.put("nodeId", r.getNodeId());
            m.put("description", r.getDescription());
            m.put("vector", r.getVector());
            list.add(m);
        }
        return list;
    }
}
