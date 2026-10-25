package common;

import java.util.*;

public class ReconciliationEngine {

    private static final List<String> STATUS_PRIORITY = List.of(
            "NEEDS_RESCUE", "UNKNOWN", "RESCUED"
    );

    public static class Resolution {
        public String resourceKey;
        public String resourceType;
        public String finalValue;
        public String method;
        public boolean needsHumanReview;
        public List<UpdateRecord> contributingUpdates;
        public String explanation;
    }

    public static List<Resolution> reconcile(List<UpdateRecord> allUpdates) {
        Map<String, List<UpdateRecord>> byResource = new LinkedHashMap<>();
        for (UpdateRecord r : allUpdates) {
            byResource.computeIfAbsent(r.getResourceKey(), k -> new ArrayList<>()).add(r);
        }

        List<Resolution> results = new ArrayList<>();
        for (Map.Entry<String, List<UpdateRecord>> entry : byResource.entrySet()) {
            String resourceKey = entry.getKey();
            List<UpdateRecord> updates = entry.getValue();
            String type = updates.get(0).getResourceType();

            if (type.equals("SET_ADD")) {
                results.add(mergeSet(resourceKey, updates));
            } else {
                results.add(resolveStatus(resourceKey, updates));
            }
        }
        return results;
    }

    private static Resolution mergeSet(String resourceKey, List<UpdateRecord> updates) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (UpdateRecord r : updates) merged.add(r.getValue());

        Resolution res = new Resolution();
        res.resourceKey = resourceKey;
        res.resourceType = "SET_ADD";
        res.finalValue = merged.toString();
        res.method = "CRDT_MERGED";
        res.needsHumanReview = false;
        res.contributingUpdates = updates;
        res.explanation = "Grow-only set: all " + updates.size()
                + " concurrently-added elements are unioned automatically, no policy needed.";
        return res;
    }

    private static Resolution resolveStatus(String resourceKey, List<UpdateRecord> updates) {
        List<UpdateRecord> frontier = causalFrontier(updates);

        Resolution res = new Resolution();
        res.resourceKey = resourceKey;
        res.resourceType = "STATUS";
        res.contributingUpdates = frontier;

        if (frontier.size() == 1) {
            UpdateRecord winner = frontier.get(0);
            res.finalValue = winner.getValue();
            res.method = updates.size() == 1 ? "SINGLE_UPDATE" : "CAUSALLY_RESOLVED";
            res.needsHumanReview = false;
            res.explanation = updates.size() == 1
                    ? "Only one update was ever made to this resource."
                    : "One update causally follows all others, so it safely wins with no conflict.";
            return res;
        }

        UpdateRecord chosen = frontier.get(0);
        for (UpdateRecord r : frontier) {
            if (priorityIndex(r.getValue()) < priorityIndex(chosen.getValue())) {
                chosen = r;
            }
        }
        res.finalValue = chosen.getValue();
        res.method = "POLICY_RESOLVED";
        res.needsHumanReview = true;
        res.explanation = frontier.size() + " concurrent, conflicting updates were found. "
                + "Safety-first policy applied: '" + chosen.getValue() + "' takes precedence "
                + "because a possible distress signal must never be silently discarded. "
                + "Flagged for human re-verification.";
        return res;
    }

    private static List<UpdateRecord> causalFrontier(List<UpdateRecord> updates) {
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

    private static int priorityIndex(String value) {
        int idx = STATUS_PRIORITY.indexOf(value);
        return idx == -1 ? Integer.MAX_VALUE : idx;
    }
}
