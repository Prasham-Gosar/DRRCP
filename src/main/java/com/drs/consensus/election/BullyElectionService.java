package com.drs.consensus.election;

import com.drs.consensus.model.ElectionNode;
import com.drs.consensus.model.ElectionResult;
import com.drs.core.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Bully Election Algorithm — Spring service port of legacy {@code election.BullyElection}.
 * <p>
 * Algorithm logic is identical to the original. All {@code System.out.println} calls
 * have been replaced with entries in the returned {@link ElectionResult#getSteps()} list,
 * and a live event is published on completion via {@link EventPublisher}.
 */
@Service
public class BullyElectionService {

    private static final Logger log = LoggerFactory.getLogger(BullyElectionService.class);

    private final EventPublisher eventPublisher;

    public BullyElectionService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Run the Bully election algorithm.
     *
     * @param nodes       all nodes participating in the election
     * @param initiatorId the node that detected the coordinator is unreachable
     * @return structured result with leader, message count, and step trace
     */
    public ElectionResult runElection(List<ElectionNode> nodes, String initiatorId) {
        List<String> steps = new ArrayList<>();
        int[] messageCount = {0};

        ElectionNode initiator = find(nodes, initiatorId);
        steps.add("[Bully] " + initiator.getId()
                + " notices the coordinator is unreachable -> starts an election.");

        Set<String> visited = new LinkedHashSet<>();
        Deque<ElectionNode> queue = new ArrayDeque<>();
        queue.add(initiator);
        visited.add(initiator.getId());

        ElectionNode leader = initiator;
        while (!queue.isEmpty()) {
            ElectionNode current = queue.poll();
            List<ElectionNode> higher = higherPriorityAlive(nodes, current);
            if (higher.isEmpty()) {
                steps.add("[Bully] " + current.getId()
                        + " receives no responses from higher-priority nodes -> declares itself leader.");
                leader = current;
                continue;
            }
            for (ElectionNode h : higher) {
                messageCount[0]++;
                steps.add("[Bully] " + current.getId() + " --ELECTION--> " + h.getId());
                messageCount[0]++;
                steps.add("[Bully]   " + h.getId() + " --ALIVE--> " + current.getId());
                if (visited.add(h.getId())) {
                    queue.add(h);
                }
            }
        }

        // COORDINATOR announcement to all alive non-leader nodes
        for (ElectionNode n : nodes) {
            if (n.isAlive() && !n.getId().equals(leader.getId())) {
                messageCount[0]++;
                steps.add("[Bully] " + leader.getId() + " --COORDINATOR--> " + n.getId());
            }
        }

        steps.add("[Bully] Election complete. Leader = " + leader.getId()
                + " (priority " + leader.getPriority() + "). Total messages exchanged = "
                + messageCount[0]);

        ElectionResult result = new ElectionResult(
                "BULLY", leader.getId(), leader.getPriority(), messageCount[0], steps);

        log.info("Bully election complete: leader={}, messages={}", leader.getId(), messageCount[0]);
        eventPublisher.publish("consensus", "LEADER_ELECTED", result);

        return result;
    }

    // ── helpers (same logic as legacy) ──

    private List<ElectionNode> higherPriorityAlive(List<ElectionNode> nodes, ElectionNode current) {
        List<ElectionNode> result = new ArrayList<>();
        for (ElectionNode n : nodes) {
            if (n.isAlive() && n.getPriority() > current.getPriority()) result.add(n);
        }
        return result;
    }

    private ElectionNode find(List<ElectionNode> nodes, String id) {
        for (ElectionNode n : nodes) if (n.getId().equals(id)) return n;
        throw new NoSuchElementException("Node not found: " + id);
    }
}
