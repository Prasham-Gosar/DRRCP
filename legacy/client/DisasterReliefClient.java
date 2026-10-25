package client;

import common.*;
import election.*;

import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * Runs the FULL pipeline in one process, using the real Cluster Coordinator
 * (started separately, see server.ClusterCoordinator) for as long as it is
 * reachable:
 *
 *   PHASE 1  Register + Cristian's clock sync + heartbeats        (Exp 1, 2, 3)
 *   PHASE 2  Simulated partition -> local leader election         (Exp 4)
 *            + vector-clock-tagged local updates                  (Exp 3)
 *   PHASE 3  Reconnect + reconcile everything that happened        (Exp 5)
 */
public class DisasterReliefClient {

    public static void main(String[] args) throws Exception {
        List<String> allNodes = Arrays.asList("UAV-01", "UAV-02", "Edge-01");

        Map<String, String> nodeToCluster = new HashMap<>();
        nodeToCluster.put("UAV-01", "Cluster-A");
        nodeToCluster.put("UAV-02", "Cluster-A");
        nodeToCluster.put("Edge-01", "Cluster-B");

        LocalBus bus = new LocalBus(nodeToCluster);
        List<UpdateRecord> globalLog = new CopyOnWriteArrayList<>();

        VectorClock vcUav1 = new VectorClock(allNodes);
        VectorClock vcUav2 = new VectorClock(allNodes);
        VectorClock vcEdge1 = new VectorClock(allNodes);

        CyclicBarrier afterPhase1 = new CyclicBarrier(3, () -> {
            System.out.println();
            System.out.println("############ NETWORK PARTITION OCCURS ############");
            bus.setPartitioned(true);

            // Exp 4: Cluster-A (2 nodes) self-organizes since the real coordinator is now unreachable.
            List<ElectionNode> forBully = new ArrayList<>(List.of(
                    new ElectionNode("UAV-01", 1, true),
                    new ElectionNode("UAV-02", 2, true)
            ));
            List<ElectionNode> forRing = new ArrayList<>(List.of(
                    new ElectionNode("UAV-01", 1, true),
                    new ElectionNode("UAV-02", 2, true)
            ));
            System.out.println("-------- Cluster-A local leader election (Bully) --------");
            BullyElection.Result bullyResult = BullyElection.runElection(forBully, "UAV-01");
            System.out.println("-------- Cluster-A local leader election (Ring) --------");
            RingElection.Result ringResult = RingElection.runElection(forRing, "UAV-01");
            System.out.println("Cluster-A temporary leader = " + bullyResult.leader.id
                    + " (Bully msgs=" + bullyResult.totalMessages + ", Ring msgs=" + ringResult.totalMessages + ")");
            System.out.println("Cluster-B has a single node (Edge-01) -- it is trivially its own leader.");
            System.out.println("####################################################");
            System.out.println();
        });

        CyclicBarrier afterPhase2 = new CyclicBarrier(3, () -> {
            System.out.println();
            System.out.println("############ CONNECTIVITY RESTORED -- clusters reconnect ############");
            bus.setPartitioned(false);
        });

        // ---- Exp 5 payloads: what each node does WHILE partitioned ----
        List<ClusterNode.Action> uav1Partition = List.of(
                ClusterNode.Action.update("House-12", "STATUS", "RESCUED",
                        "Found and evacuated occupant at House-12")
        );
        List<ClusterNode.Action> uav2Partition = List.of(
                ClusterNode.Action.update("confirmedSurvivors", "SET_ADD", "Meera Joshi",
                        "Confirmed survivor Meera Joshi at temporary shelter")
        );
        List<ClusterNode.Action> edge1Partition = List.of(
                ClusterNode.Action.update("House-12", "STATUS", "NEEDS_RESCUE",
                        "Ground team reports House-12 occupant still trapped"),
                ClusterNode.Action.update("confirmedSurvivors", "SET_ADD", "Raghav Iyer",
                        "Confirmed survivor Raghav Iyer near relay point")
        );

        ClusterNode uav1 = new ClusterNode("UAV-01", "Cluster-A", "UAV", 80, 70, 1500,
                vcUav1, bus, globalLog, uav1Partition, List.of(), afterPhase1, afterPhase2);
        ClusterNode uav2 = new ClusterNode("UAV-02", "Cluster-A", "UAV", 60, 55, -800,
                vcUav2, bus, globalLog, uav2Partition, List.of(), afterPhase1, afterPhase2);
        ClusterNode edge1 = new ClusterNode("Edge-01", "Cluster-B", "EDGE", 90, 85, 300,
                vcEdge1, bus, globalLog, edge1Partition, List.of(), afterPhase1, afterPhase2);

        System.out.println("=== PHASE 1: registering with the Cluster Coordinator, syncing clocks, heartbeats ===");
        uav1.start(); uav2.start(); edge1.start();
        uav1.join(); uav2.join(); edge1.join();

        // ---- Exp 5: reconcile everything that happened across both clusters ----
        System.out.println();
        System.out.println("=== PHASE 3: RECONCILIATION (Exp 5) =====================================");
        List<ReconciliationEngine.Resolution> results = ReconciliationEngine.reconcile(globalLog);
        for (ReconciliationEngine.Resolution res : results) {
            System.out.println("Resource: " + res.resourceKey + " (" + res.resourceType + ")");
            System.out.println("   Method: " + res.method + "  Final: " + res.finalValue
                    + "  NeedsHumanReview: " + res.needsHumanReview);
            System.out.println("   " + res.explanation);
        }
        System.out.println("===========================================================================");
        System.out.println("Full pipeline complete: register -> sync -> heartbeat -> partition -> elect -> reconcile.");
    }
}
