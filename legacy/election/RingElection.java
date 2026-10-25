package election;

import common.ElectionNode;

import java.util.*;

public class RingElection {

    public static class Result {
        public ElectionNode leader;
        public int totalMessages;
    }

    public static Result runElection(List<ElectionNode> ring, String initiatorId) {
        int messageCount = 0;
        int startIdx = indexOf(ring, initiatorId);
        System.out.println("[Ring] " + initiatorId + " notices the coordinator is unreachable -> starts an election.");

        ElectionNode candidate = ring.get(startIdx);
        int idx = startIdx;
        do {
            int nextIdx = nextAlive(ring, idx);
            ElectionNode from = ring.get(idx);
            ElectionNode to = ring.get(nextIdx);
            messageCount++;
            System.out.println("[Ring] " + from.id + " --ELECTION(candidate=" + candidate.id + ")--> " + to.id);
            if (to.priority > candidate.priority) {
                candidate = to;
            }
            idx = nextIdx;
        } while (!ring.get(idx).id.equals(initiatorId));

        System.out.println("[Ring] Election message returns to " + initiatorId
                + ". Highest-priority candidate seen = " + candidate.id + " -> declared leader.");
        ElectionNode leader = candidate;

        idx = startIdx;
        do {
            int nextIdx = nextAlive(ring, idx);
            ElectionNode from = ring.get(idx);
            ElectionNode to = ring.get(nextIdx);
            messageCount++;
            System.out.println("[Ring] " + from.id + " --COORDINATOR(leader=" + leader.id + ")--> " + to.id);
            idx = nextIdx;
        } while (!ring.get(idx).id.equals(initiatorId));

        System.out.println("[Ring] Election complete. Leader = " + leader.id
                + " (priority " + leader.priority + "). Total messages exchanged = " + messageCount);

        Result res = new Result();
        res.leader = leader;
        res.totalMessages = messageCount;
        return res;
    }

    private static int nextAlive(List<ElectionNode> ring, int fromIdx) {
        int n = ring.size();
        int idx = fromIdx;
        for (int step = 0; step < n; step++) {
            idx = (idx + 1) % n;
            if (ring.get(idx).alive) return idx;
        }
        throw new IllegalStateException("No alive nodes in ring");
    }

    private static int indexOf(List<ElectionNode> ring, String id) {
        for (int i = 0; i < ring.size(); i++) if (ring.get(i).id.equals(id)) return i;
        throw new NoSuchElementException(id);
    }
}
