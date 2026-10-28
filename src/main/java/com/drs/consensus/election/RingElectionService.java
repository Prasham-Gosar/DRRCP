package com.drs.consensus.election;

import com.drs.consensus.model.ElectionNode;
import com.drs.consensus.model.ElectionResult;
import com.drs.core.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Ring Election Algorithm — Spring service port of legacy {@code election.RingElection}.
 * <p>
 * Algorithm logic is identical to the original. All {@code System.out.println} calls
 * have been replaced with entries in the returned {@link ElectionResult#getSteps()} list,
 * and a live event is published on completion via {@link EventPublisher}.
 */
@Service
public class RingElectionService {

    private static final Logger log = LoggerFactory.getLogger(RingElectionService.class);

    private final EventPublisher eventPublisher;

    public RingElectionService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Run the Ring election algorithm.
     *
     * @param ring        ordered list of nodes forming the logical ring
     * @param initiatorId the node that detected the coordinator is unreachable
     * @return structured result with leader, message count, and step trace
     */
    public ElectionResult runElection(List<ElectionNode> ring, String initiatorId) {
        List<String> steps = new ArrayList<>();
        int messageCount = 0;

        int startIdx = indexOf(ring, initiatorId);
        steps.add("[Ring] " + initiatorId
                + " notices the coordinator is unreachable -> starts an election.");

        boolean anyAlive = ring.stream().anyMatch(ElectionNode::isAlive);
        if (!anyAlive) {
            steps.add("[Ring] All nodes in ring are dead. No alive node can be elected leader.");
            return new ElectionResult("RING", "NO_LEADER", -1, 0, steps);
        }

        ElectionNode candidate = ring.get(startIdx);
        int idx = startIdx;

        // Phase 1: pass the ELECTION message around the ring
        do {
            int nextIdx = nextAlive(ring, idx);
            ElectionNode from = ring.get(idx);
            ElectionNode to = ring.get(nextIdx);
            messageCount++;
            steps.add("[Ring] " + from.getId() + " --ELECTION(candidate="
                    + candidate.getId() + ")--> " + to.getId());
            if (to.getPriority() > candidate.getPriority()) {
                candidate = to;
            }
            idx = nextIdx;
        } while (!ring.get(idx).getId().equals(initiatorId));

        steps.add("[Ring] Election message returns to " + initiatorId
                + ". Highest-priority candidate seen = " + candidate.getId()
                + " -> declared leader.");
        ElectionNode leader = candidate;

        // Phase 2: pass the COORDINATOR announcement around the ring
        idx = startIdx;
        do {
            int nextIdx = nextAlive(ring, idx);
            ElectionNode from = ring.get(idx);
            ElectionNode to = ring.get(nextIdx);
            messageCount++;
            steps.add("[Ring] " + from.getId() + " --COORDINATOR(leader="
                    + leader.getId() + ")--> " + to.getId());
            idx = nextIdx;
        } while (!ring.get(idx).getId().equals(initiatorId));

        steps.add("[Ring] Election complete. Leader = " + leader.getId()
                + " (priority " + leader.getPriority() + "). Total messages exchanged = "
                + messageCount);

        ElectionResult result = new ElectionResult(
                "RING", leader.getId(), leader.getPriority(), messageCount, steps);

        log.info("Ring election complete: leader={}, messages={}", leader.getId(), messageCount);
        eventPublisher.publish("consensus", "LEADER_ELECTED", result);

        return result;
    }

    // ── helpers (same logic as legacy) ──

    private int nextAlive(List<ElectionNode> ring, int fromIdx) {
        int n = ring.size();
        int idx = fromIdx;
        for (int step = 0; step < n; step++) {
            idx = (idx + 1) % n;
            if (ring.get(idx).isAlive()) return idx;
        }
        throw new IllegalStateException("No alive nodes in ring");
    }

    private int indexOf(List<ElectionNode> ring, String id) {
        for (int i = 0; i < ring.size(); i++) {
            if (ring.get(i).getId().equals(id)) return i;
        }
        throw new NoSuchElementException("Node not found in ring: " + id);
    }
}
