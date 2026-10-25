package server;

import common.*;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.*;

public class ClusterCoordinatorImpl extends UnicastRemoteObject implements NodeService {

    // Exp2: fixed worker pool instead of handling one request at a time
    private final ExecutorService workerPool = Executors.newFixedThreadPool(4);

    private final ConcurrentHashMap<String, String> stateTable = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> heartbeatLog = new ConcurrentHashMap<>();

    protected ClusterCoordinatorImpl() throws RemoteException {
        super();
    }

    // ---------- Exp 1: registration + connectivity scoring ----------
    @Override
    public String registerNode(NodeStatus node) throws RemoteException {
        Future<String> future = workerPool.submit(() -> handleRegister(node));
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "ERROR: worker pool failed (" + e.getMessage() + ")";
        }
    }

    private String handleRegister(NodeStatus node) {
        int score = (2 * node.getSignalStrength()) + node.getBatteryLevel();
        String state;
        if (score >= 150) state = "STABLE";
        else if (score >= 100) state = "AT_RISK";
        else if (score >= 50) state = "PARTITION_LIKELY";
        else state = "PARTITIONED";

        stateTable.put(node.getNodeId(), state);
        heartbeatLog.put(node.getNodeId(), System.currentTimeMillis());

        System.out.println("[" + Thread.currentThread().getName() + "] Registered "
                + node.getNodeId() + " (" + node.getClusterId() + "/" + node.getNodeType()
                + ") battery=" + node.getBatteryLevel() + " signal=" + node.getSignalStrength()
                + " score=" + score + " -> " + state);
        return "Registration Successful";
    }

    @Override
    public String getNodeState(String nodeId) throws RemoteException {
        return stateTable.get(nodeId);
    }

    // ---------- Exp 2: concurrent heartbeats + silence monitor ----------
    @Override
    public String sendHeartbeat(String nodeId, long timestamp) throws RemoteException {
        Future<String> future = workerPool.submit(() -> {
            heartbeatLog.put(nodeId, timestamp);
            System.out.println("[" + Thread.currentThread().getName()
                    + "] Heartbeat received from " + nodeId);
            return "Heartbeat OK";
        });
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Called every second by a background monitor thread started from main(). */
    public void checkForSilentNodes(long thresholdMs) {
        long now = System.currentTimeMillis();
        for (var entry : heartbeatLog.entrySet()) {
            long silentFor = now - entry.getValue();
            if (silentFor > thresholdMs) {
                System.out.println("[Monitor] PARTITION SUSPECTED: node '" + entry.getKey()
                        + "' silent for " + silentFor + "ms (threshold=" + thresholdMs + "ms)");
            }
        }
    }

    // ---------- Exp 3: Cristian's Algorithm time server ----------
    @Override
    public long getTime() throws RemoteException {
        try {
            // small random processing delay, exactly like the standalone Exp3 server
            Thread.sleep(ThreadLocalRandom.current().nextInt(10, 60));
        } catch (InterruptedException ignored) { }
        return System.currentTimeMillis();
    }
}
