package election;

import common.ElectionNode;

import java.util.*;

public class BullyElection {

    public static class Result {
        public ElectionNode leader;
        public int totalMessages;
    }

    public static Result runElection(List<ElectionNode> nodes, String initiatorId) {
        int[] messageCount = {0};
        ElectionNode initiator = find(nodes, initiatorId);
        System.out.println("[Bully] " + initiator.id + " notices the coordinator is unreachable -> starts an election.");

        Set<String> visited = new LinkedHashSet<>();
        Deque<ElectionNode> queue = new ArrayDeque<>();
        queue.add(initiator);
        visited.add(initiator.id);

        ElectionNode leader = null;
        while (!queue.isEmpty()) {
            ElectionNode current = queue.poll();
            List<ElectionNode> higher = higherPriorityAlive(nodes, current);
            if (higher.isEmpty()) {
                System.out.println("[Bully] " + current.id
                        + " receives no responses from higher-priority nodes -> declares itself leader.");
                if (leader == null) leader = current;
                continue;
            }
            for (ElectionNode h : higher) {
                messageCount[0]++;
                System.out.println("[Bully] " + current.id + " --ELECTION--> " + h.id);
                messageCount[0]++;
                System.out.println("[Bully]   " + h.id + " --ALIVE--> " + current.id);
                if (visited.add(h.id)) {
                    queue.add(h);
                }
            }
        }

        for (ElectionNode n : nodes) {
            if (n.alive && !n.id.equals(leader.id)) {
                messageCount[0]++;
                System.out.println("[Bully] " + leader.id + " --COORDINATOR--> " + n.id);
            }
        }

        System.out.println("[Bully] Election complete. Leader = " + leader.id
                + " (priority " + leader.priority + "). Total messages exchanged = " + messageCount[0]);

        Result res = new Result();
        res.leader = leader;
        res.totalMessages = messageCount[0];
        return res;
    }

    private static List<ElectionNode> higherPriorityAlive(List<ElectionNode> nodes, ElectionNode current) {
        List<ElectionNode> result = new ArrayList<>();
        for (ElectionNode n : nodes) {
            if (n.alive && n.priority > current.priority) result.add(n);
        }
        return result;
    }

    private static ElectionNode find(List<ElectionNode> nodes, String id) {
        for (ElectionNode n : nodes) if (n.id.equals(id)) return n;
        throw new NoSuchElementException(id);
    }
}
