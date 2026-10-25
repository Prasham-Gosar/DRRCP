package client;

import common.UpdateRecord;

import java.util.*;
import java.util.concurrent.*;

/**
 * Simulates the wireless link between edge nodes. When the system is
 * "partitioned", messages can only cross between nodes in the SAME cluster --
 * exactly like Experiments 3 and 5.
 */
public class LocalBus {
    private final Map<String, BlockingQueue<UpdateRecord>> inboxes = new ConcurrentHashMap<>();
    private final Map<String, String> nodeToCluster;
    private volatile boolean partitioned = false;

    public LocalBus(Map<String, String> nodeToCluster) {
        this.nodeToCluster = nodeToCluster;
        for (String node : nodeToCluster.keySet()) {
            inboxes.put(node, new LinkedBlockingQueue<>());
        }
    }

    public void setPartitioned(boolean value) {
        partitioned = value;
    }

    public boolean send(String fromNode, String toNode, UpdateRecord record) {
        String fromCluster = nodeToCluster.get(fromNode);
        String toCluster = nodeToCluster.get(toNode);
        if (partitioned && !fromCluster.equals(toCluster)) {
            System.out.println("   [NETWORK] " + fromNode + " -> " + toNode
                    + " BLOCKED (clusters are partitioned)");
            return false;
        }
        inboxes.get(toNode).offer(record);
        return true;
    }

    public List<UpdateRecord> drain(String nodeId) {
        List<UpdateRecord> received = new ArrayList<>();
        inboxes.get(nodeId).drainTo(received);
        return received;
    }
}
