package client;

import common.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class ClusterNode extends Thread {

    /** One locally-made update, applied and (maybe) forwarded during the partition. */
    public static class Action {
        String resourceKey, resourceType, value, description, sendTo;

        public static Action update(String resourceKey, String resourceType, String value, String desc) {
            Action a = new Action();
            a.resourceKey = resourceKey; a.resourceType = resourceType;
            a.value = value; a.description = desc;
            return a;
        }

        public static Action updateAndSend(String resourceKey, String resourceType, String value,
                                            String desc, String sendTo) {
            Action a = update(resourceKey, resourceType, value, desc);
            a.sendTo = sendTo;
            return a;
        }
    }

    private final String nodeId, clusterId, nodeType;
    private final int battery, signal;
    private final long injectedSkewMillis;   // simulated clock drift, corrected by Cristian's Algorithm
    private final VectorClock clock;
    private final LocalBus bus;
    private final List<UpdateRecord> globalLog;   // shared, thread-safe -- feeds Exp5 at the end
    private final List<Action> partitionActions, reconnectActions;
    private final CyclicBarrier afterPhase1, afterPhase2;
    private int counter = 0;

    public ClusterNode(String nodeId, String clusterId, String nodeType, int battery, int signal,
                        long injectedSkewMillis, VectorClock clock, LocalBus bus,
                        List<UpdateRecord> globalLog,
                        List<Action> partitionActions, List<Action> reconnectActions,
                        CyclicBarrier afterPhase1, CyclicBarrier afterPhase2) {
        this.nodeId = nodeId;
        this.clusterId = clusterId;
        this.nodeType = nodeType;
        this.battery = battery;
        this.signal = signal;
        this.injectedSkewMillis = injectedSkewMillis;
        this.clock = clock;
        this.bus = bus;
        this.globalLog = globalLog;
        this.partitionActions = partitionActions;
        this.reconnectActions = reconnectActions;
        this.afterPhase1 = afterPhase1;
        this.afterPhase2 = afterPhase2;
    }

    private long localClock() {
        return System.currentTimeMillis() + injectedSkewMillis;
    }

    @Override
    public void run() {
        NodeService coordinator = null;
        try {
            // ---------------- PHASE 1: connected -- Exp1 + Exp3(physical) + Exp2 ----------------
            try {
                Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                coordinator = (NodeService) registry.lookup("Coordinator");

                String resp = coordinator.registerNode(
                        new NodeStatus(nodeId, battery, signal, nodeType, clusterId));
                System.out.println("[" + nodeId + "] " + resp + " | state="
                        + coordinator.getNodeState(nodeId));

                cristianSync(coordinator);

                for (int i = 0; i < 3; i++) {
                    coordinator.sendHeartbeat(nodeId, System.currentTimeMillis());
                    Thread.sleep(300);
                }
                System.out.println("[" + nodeId + "] Sent initial heartbeats. Waiting for cluster...");
            } catch (Exception e) {
                System.out.println("[" + nodeId + "] Could not reach the Coordinator ("
                        + e.getMessage() + "). Is 'java server.ClusterCoordinator' running?");
            }

            clock.tick(nodeId);
            globalLog.add(new UpdateRecord(nodeId + "-U" + (++counter), "patrol-log", "SET_ADD",
                    nodeId + " began patrol", clusterId, nodeId, "Patrol start", clock.snapshot()));

            afterPhase1.await(); // barrier action (in Main) declares the partition + runs Exp4 election

            // ---------------- PHASE 2: partitioned -- Exp3(logical) + Exp5(tagging) ----------------
            runActions(partitionActions);

            afterPhase2.await(); // barrier action (in Main) declares reconnection

            // ---------------- PHASE 3: reconnected -- Exp2 heartbeat resumes + Exp5 sync ----------------
            drainInbox();
            runActions(reconnectActions);
            drainInbox();

            if (coordinator != null) {
                try {
                    coordinator.sendHeartbeat(nodeId, System.currentTimeMillis());
                    System.out.println("[" + nodeId + "] Reconnected -- heartbeat to Coordinator resumed.");
                } catch (Exception e) {
                    System.out.println("[" + nodeId + "] Still could not reach Coordinator: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Exp3, part 1 -- Cristian's Algorithm. */
    private void cristianSync(NodeService coordinator) throws Exception {
        long before = localClock();
        System.out.println("[" + nodeId + "] Local clock BEFORE sync = " + before
                + " (injected drift = " + injectedSkewMillis + " ms)");

        long t0 = localClock();
        long serverTime = coordinator.getTime();
        long t1 = localClock();

        long rtt = t1 - t0;
        long oneWayDelay = rtt / 2;
        long corrected = serverTime + oneWayDelay;

        System.out.println("[" + nodeId + "] RTT=" + rtt + "ms | corrected clock = " + corrected
                + " | remaining error ~ " + (corrected - System.currentTimeMillis()) + "ms");
    }

    private void drainInbox() {
        for (UpdateRecord incoming : bus.drain(nodeId)) {
            clock.mergeWith(incoming.getVector());
            clock.tick(nodeId);
            globalLog.add(incoming);
            System.out.println("   [" + nodeId + "] received update on '" + incoming.getResourceKey()
                    + "' = " + incoming.getValue() + " from " + incoming.getNodeId()
                    + " | VC now " + clock.snapshot());
        }
    }

    /** Exp5-style: apply a local update, tag it with the current vector clock, maybe forward it. */
    private void runActions(List<Action> actions) {
        for (Action a : actions) {
            drainInbox();
            sleep(150 + new Random().nextInt(150));
            clock.tick(nodeId);

            UpdateRecord r = new UpdateRecord(nodeId + "-U" + (++counter), a.resourceKey,
                    a.resourceType, a.value, clusterId, nodeId, a.description, clock.snapshot());
            globalLog.add(r);

            System.out.println("[" + clusterId + "][" + nodeId + "] " + a.description
                    + " | " + a.resourceKey + " = " + a.value + " | VC " + clock.snapshot());

            if (a.sendTo != null) {
                bus.send(nodeId, a.sendTo, r);
            }
        }
    }

    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
