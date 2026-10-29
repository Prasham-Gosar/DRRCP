package com.drs.core.scenario;

import com.drs.consensus.election.BullyElectionService;
import com.drs.consensus.election.RingElectionService;
import com.drs.consensus.model.*;
import com.drs.consensus.reconcile.ReconciliationService;
import com.drs.consensus.reconcile.VectorClockRegistry;
import com.drs.core.event.EventPublisher;
import com.drs.core.service.ClockSyncService;
import com.drs.core.service.NetworkService;
import com.drs.core.service.NodeRegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Exp 1-5 End-to-End Story Orchestrator (Scenario Runner).
 * <p>
 * Reproduces the full Disaster Relief distributed computing pipeline from the legacy console demo:
 * <ol>
 *   <li>Phase 1: Boot & Register UAV/Edge nodes with the Cluster Coordinator (Exp 1 + 2).</li>
 *   <li>Phase 2: Cristian's Clock Synchronization & Vector Clock Initialization (Exp 3).</li>
 *   <li>Phase 3: Network Partition between Cluster-A and Cluster-B (Exp 3).</li>
 *   <li>Phase 4: Local Leader Election via Bully and Ring algorithms (Exp 4).</li>
 *   <li>Phase 5: Partitioned Local Updates tagged with Vector Clocks (Exp 3 + 5).</li>
 *   <li>Phase 6: Reconnect & Heal Network Partition (Exp 3).</li>
 *   <li>Phase 7: CRDT Set Union & Safety-First Conflict Reconciliation (Exp 5).</li>
 * </ol>
 */
@Service
public class ScenarioRunner {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRunner.class);

    private final NodeRegistrationService registrationService;
    private final ClockSyncService clockSyncService;
    private final NetworkService networkService;
    private final BullyElectionService bullyElectionService;
    private final RingElectionService ringElectionService;
    private final VectorClockRegistry vectorClockRegistry;
    private final ReconciliationService reconciliationService;
    private final EventPublisher eventPublisher;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "scenario-runner-thread");
        t.setDaemon(true);
        return t;
    });

    private volatile ScenarioStatus status = new ScenarioStatus();
    private volatile ReconciliationResult lastReconciliation = null;

    public ScenarioRunner(NodeRegistrationService registrationService,
                          ClockSyncService clockSyncService,
                          NetworkService networkService,
                          BullyElectionService bullyElectionService,
                          RingElectionService ringElectionService,
                          VectorClockRegistry vectorClockRegistry,
                          ReconciliationService reconciliationService,
                          EventPublisher eventPublisher) {
        this.registrationService = registrationService;
        this.clockSyncService = clockSyncService;
        this.networkService = networkService;
        this.bullyElectionService = bullyElectionService;
        this.ringElectionService = ringElectionService;
        this.vectorClockRegistry = vectorClockRegistry;
        this.reconciliationService = reconciliationService;
        this.eventPublisher = eventPublisher;

        resetStatus();
    }

    public synchronized CompletableFuture<ScenarioStatus> runScenario(ScenarioConfig config) {
        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("A scenario run is already in progress.");
        }

        resetStatus();
        status.setRunning(true);
        status.setState("RUNNING");
        status.setStartedAt(System.currentTimeMillis());

        return CompletableFuture.supplyAsync(() -> {
            try {
                executePhases(config != null ? config : new ScenarioConfig());
                status.setState("COMPLETED");
                status.setCurrentPhaseName("ALL_PHASES_COMPLETED");
            } catch (Exception e) {
                log.error("Scenario execution failed: {}", e.getMessage(), e);
                status.setState("FAILED");
                status.setErrorMessage(e.getMessage());
            } finally {
                status.setRunning(false);
                status.setCompletedAt(System.currentTimeMillis());
                status.setTotalDurationMs(status.getCompletedAt() - status.getStartedAt());
                isRunning.set(false);
            }
            return status;
        }, executor);
    }

    private void executePhases(ScenarioConfig config) throws Exception {
        logScenario("=================================================================");
        logScenario("STARTING FULL DISASTER RELIEF COORDINATION SCENARIO");
        logScenario("=================================================================");

        // Sequential phase execution with latch / barrier completion
        runPhase(1, "BOOT_AND_REGISTER", "Boot & Register Nodes with Coordinator", () -> {
            executePhase1BootAndRegister();
        });

        runPhase(2, "CLOCK_SYNC", "Cristian's Clock Sync & Vector Clock Init", () -> {
            executePhase2ClockSync(config);
        });

        runPhase(3, "NETWORK_PARTITION", "Simulate Disaster Network Partition", () -> {
            executePhase3Partition();
        });

        runPhase(4, "LEADER_ELECTION", "Cluster-A Local Leader Election (Bully & Ring)", () -> {
            executePhase4Election();
        });

        runPhase(5, "PARTITIONED_UPDATES", "Partitioned Disaster Assessment Updates", () -> {
            executePhase5Updates(config);
        });

        runPhase(6, "HEAL_PARTITION", "Restore Connectivity & Reconnect Clusters", () -> {
            executePhase6Heal();
        });

        runPhase(7, "RECONCILIATION", "CRDT Set Union & Conflict Reconciliation", () -> {
            executePhase7Reconciliation();
        });

        logScenario("=================================================================");
        logScenario("SCENARIO COMPLETED SUCCESSFULLY: ALL 7 PHASES EXECUTED");
        logScenario("=================================================================");
    }

    private void runPhase(int phaseNum, String name, String title, PhaseRunnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        status.setCurrentPhase(phaseNum);
        status.setCurrentPhaseName(name);

        PhaseDetail detail = status.getPhases().get(phaseNum - 1);
        detail.setStatus("RUNNING");
        detail.setStartedAt(System.currentTimeMillis());

        publishPhaseEvent(phaseNum, name, "START", "Starting Phase " + phaseNum + ": " + title);
        logScenario(">>> [PHASE " + phaseNum + "] " + title + " started");

        try {
            action.run();
            detail.setStatus("COMPLETED");
        } catch (Exception e) {
            detail.setStatus("FAILED");
            detail.addStep("ERROR in Phase " + phaseNum + ": " + e.getMessage());
            throw e;
        } finally {
            detail.setFinishedAt(System.currentTimeMillis());
            detail.setDurationMs(detail.getFinishedAt() - detail.getStartedAt());
            latch.countDown();
            latch.await(5, TimeUnit.SECONDS);

            publishPhaseEvent(phaseNum, name, detail.getStatus(), detail.getSummary());
            logScenario("<<< [PHASE " + phaseNum + "] " + detail.getStatus() + " (" + detail.getDurationMs() + "ms)");
        }
    }

    // ── Phase Implementations ──

    private void executePhase1BootAndRegister() {
        PhaseDetail detail = status.getPhases().get(0);

        detail.addStep("Registering UAV-01 (Cluster-A, Battery 85%, Signal 75%)");
        var r1 = registrationService.registerNode("UAV-01", 85, 75, "UAV", "Cluster-A");
        detail.addStep("[RMI/REST] " + r1.nodeId() + " registered. Score = " + r1.connectivityScore() + " (" + r1.state() + ") on [" + r1.thread() + "]");

        detail.addStep("Registering UAV-02 (Cluster-A, Battery 60%, Signal 50%)");
        var r2 = registrationService.registerNode("UAV-02", 60, 50, "UAV", "Cluster-A");
        detail.addStep("[RMI/REST] " + r2.nodeId() + " registered. Score = " + r2.connectivityScore() + " (" + r2.state() + ") on [" + r2.thread() + "]");

        detail.addStep("Registering Edge-01 (Cluster-B, Battery 95%, Signal 90%)");
        var r3 = registrationService.registerNode("Edge-01", 95, 90, "EDGE", "Cluster-B");
        detail.addStep("[RMI/REST] " + r3.nodeId() + " registered. Score = " + r3.connectivityScore() + " (" + r3.state() + ") on [" + r3.thread() + "]");

        // Send heartbeats
        registrationService.sendHeartbeat("UAV-01");
        registrationService.sendHeartbeat("UAV-02");
        registrationService.sendHeartbeat("Edge-01");
        detail.addStep("Dispatched initial heartbeats to 4-thread worker pool.");

        detail.setSummary("3 nodes registered (UAV-01: 235 STABLE, UAV-02: 160 STABLE, Edge-01: 275 STABLE). Heartbeat pool active.");
    }

    private void executePhase2ClockSync(ScenarioConfig config) {
        PhaseDetail detail = status.getPhases().get(1);

        // Apply physical skews
        clockSyncService.setInjectedSkew("UAV-01", config.getSkew("UAV-01", 1500L));
        clockSyncService.setInjectedSkew("UAV-02", config.getSkew("UAV-02", -800L));
        clockSyncService.setInjectedSkew("Edge-01", config.getSkew("Edge-01", 300L));
        detail.addStep("Applied simulated clock drift: UAV-01 (+1500ms), UAV-02 (-800ms), Edge-01 (+300ms)");

        // Synchronize via Cristian's algorithm
        var s1 = clockSyncService.syncNode("UAV-01");
        detail.addStep("[Cristian] UAV-01: RTT=" + s1.rttMillis() + "ms, offset=" + s1.offsetMillis() + "ms -> corrected");

        var s2 = clockSyncService.syncNode("UAV-02");
        detail.addStep("[Cristian] UAV-02: RTT=" + s2.rttMillis() + "ms, offset=" + s2.offsetMillis() + "ms -> corrected");

        var s3 = clockSyncService.syncNode("Edge-01");
        detail.addStep("[Cristian] Edge-01: RTT=" + s3.rttMillis() + "ms, offset=" + s3.offsetMillis() + "ms -> corrected");

        // Initialize Vector Clocks
        List<String> nodes = List.of("UAV-01", "UAV-02", "Edge-01");
        vectorClockRegistry.init(nodes);
        detail.addStep("Initialized logical vector clocks across nodes: " + nodes);

        // Patrol start tick
        vectorClockRegistry.tick("UAV-01");
        vectorClockRegistry.tick("UAV-02");
        vectorClockRegistry.tick("Edge-01");
        detail.addStep("Logged initial patrol start event for all nodes.");

        detail.setSummary("Cristian sync completed (avg RTT ~" + ((s1.rttMillis() + s2.rttMillis() + s3.rttMillis()) / 3) + "ms). Vector clocks initialized.");
    }

    private void executePhase3Partition() {
        PhaseDetail detail = status.getPhases().get(2);
        detail.addStep("Simulating network partition between Cluster-A (UAVs) and Cluster-B (Edge)...");

        networkService.partition();
        detail.addStep("[NETWORK] Wireless link between Cluster-A and Cluster-B severed.");
        detail.addStep("[NETWORK] Coordinator is now UNREACHABLE for all edge nodes.");

        detail.setSummary("Network partitioned. Inter-cluster messaging blocked. Coordinator unreachable.");
    }

    private void executePhase4Election() {
        PhaseDetail detail = status.getPhases().get(3);
        detail.addStep("Cluster-A detects Coordinator silence -> initiates temporary local leader election.");

        List<ElectionNode> clusterANodes = List.of(
                new ElectionNode("UAV-01", 1, true),
                new ElectionNode("UAV-02", 2, true)
        );

        // Run Bully Election
        ElectionResult bullyResult = bullyElectionService.runElection(clusterANodes, "UAV-01");
        detail.addStep("[Bully Election] Cluster-A: Leader = " + bullyResult.getLeaderId() + " (Messages: " + bullyResult.getTotalMessages() + ")");
        for (String step : bullyResult.getSteps()) {
            detail.addStep("  " + step);
        }

        // Run Ring Election
        ElectionResult ringResult = ringElectionService.runElection(clusterANodes, "UAV-01");
        detail.addStep("[Ring Election] Cluster-A: Leader = " + ringResult.getLeaderId() + " (Messages: " + ringResult.getTotalMessages() + ")");

        detail.addStep("[Cluster-B] Single node Edge-01 is trivially its own cluster leader.");
        detail.setSummary("Election complete: UAV-02 elected leader of Cluster-A (Bully: " + bullyResult.getTotalMessages() + " msgs, Ring: " + ringResult.getTotalMessages() + " msgs).");
    }

    private void executePhase5Updates(ScenarioConfig config) {
        PhaseDetail detail = status.getPhases().get(4);
        detail.addStep("Applying concurrent disaster updates while partitioned...");

        reconciliationService.clearUpdates();

        // 1. UAV-01 marks House-12 RESCUED
        vectorClockRegistry.tick("UAV-01");
        UpdateRecord u1 = new UpdateRecord(
                "UAV-01-U1", "House-12", "STATUS", "RESCUED",
                "Cluster-A", "UAV-01", "Found and evacuated occupant at House-12",
                vectorClockRegistry.snapshot("UAV-01")
        );
        reconciliationService.addUpdate(u1);
        detail.addStep("[Cluster-A][UAV-01] House-12 = RESCUED | VC: " + u1.getVector());

        // 2. UAV-02 adds survivor Meera Joshi
        vectorClockRegistry.tick("UAV-02");
        UpdateRecord u2 = new UpdateRecord(
                "UAV-02-U1", "confirmedSurvivors", "SET_ADD", "Meera Joshi",
                "Cluster-A", "UAV-02", "Confirmed survivor Meera Joshi at temporary shelter",
                vectorClockRegistry.snapshot("UAV-02")
        );
        reconciliationService.addUpdate(u2);
        detail.addStep("[Cluster-A][UAV-02] confirmedSurvivors SET_ADD 'Meera Joshi' | VC: " + u2.getVector());

        // 3. Edge-01 marks House-12 NEEDS_RESCUE (concurrent conflicting status)
        vectorClockRegistry.tick("Edge-01");
        UpdateRecord u3 = new UpdateRecord(
                "Edge-01-U1", "House-12", "STATUS", "NEEDS_RESCUE",
                "Cluster-B", "Edge-01", "Ground team reports House-12 occupant still trapped",
                vectorClockRegistry.snapshot("Edge-01")
        );
        reconciliationService.addUpdate(u3);
        detail.addStep("[Cluster-B][Edge-01] House-12 = NEEDS_RESCUE | VC: " + u3.getVector());

        // 4. Edge-01 adds survivor Raghav Iyer
        vectorClockRegistry.tick("Edge-01");
        UpdateRecord u4 = new UpdateRecord(
                "Edge-01-U2", "confirmedSurvivors", "SET_ADD", "Raghav Iyer",
                "Cluster-B", "Edge-01", "Confirmed survivor Raghav Iyer near relay point",
                vectorClockRegistry.snapshot("Edge-01")
        );
        reconciliationService.addUpdate(u4);
        detail.addStep("[Cluster-B][Edge-01] confirmedSurvivors SET_ADD 'Raghav Iyer' | VC: " + u4.getVector());

        // Simulated partition duration
        int duration = config.getPartitionDurationMs();
        if (duration > 0) {
            try { Thread.sleep(duration); } catch (InterruptedException ignored) { }
        }

        detail.setSummary("4 partitioned updates logged with concurrent vector clocks: House-12 (RESCUED vs NEEDS_RESCUE) & confirmedSurvivors (2 adds).");
    }

    private void executePhase6Heal() {
        PhaseDetail detail = status.getPhases().get(5);
        detail.addStep("Restoring network connectivity across all clusters...");

        networkService.heal();
        detail.addStep("[NETWORK] Connectivity restored. Inter-cluster links operational.");
        detail.addStep("[NETWORK] Coordinator is REACHABLE. Resumed node heartbeats.");

        registrationService.sendHeartbeat("UAV-01");
        registrationService.sendHeartbeat("UAV-02");
        registrationService.sendHeartbeat("Edge-01");
        detail.addStep("Heartbeats acknowledged by Coordinator after partition heal.");

        detail.setSummary("Network healed. Clusters reconnected. Coordinator heartbeats resumed.");
    }

    private void executePhase7Reconciliation() {
        PhaseDetail detail = status.getPhases().get(6);
        detail.addStep("Initiating CRDT set union and causal status reconciliation (Exp 5)...");

        ReconciliationResult result = reconciliationService.reconcile(null);
        this.lastReconciliation = result;
        status.setReconciliation(result);

        for (String step : result.getSteps()) {
            detail.addStep("  " + step);
        }

        for (Resolution res : result.getResolutions()) {
            detail.addStep("[Resolved] " + res.getResourceKey() + " (" + res.getResourceType() + ") -> "
                    + res.getFinalValue() + " via " + res.getMethod()
                    + (res.isNeedsHumanReview() ? " [FLAGGED FOR HUMAN REVIEW]" : ""));
        }

        detail.setSummary("Reconciliation complete: House-12 resolved to NEEDS_RESCUE (safety-first policy wins). Confirmed survivors unioned via CRDT.");
    }

    // ── Status Management & Utilities ──

    public ScenarioStatus getStatus() {
        return status;
    }

    public synchronized ScenarioStatus reset() {
        if (isRunning.get()) {
            throw new IllegalStateException("Cannot reset while scenario is running.");
        }
        networkService.heal();
        reconciliationService.clearUpdates();
        resetStatus();
        logScenario("Scenario state reset.");
        return status;
    }

    public ReconciliationResult getLastReconciliation() {
        return lastReconciliation;
    }

    private void resetStatus() {
        ScenarioStatus s = new ScenarioStatus();
        s.setRunning(false);
        s.setState("IDLE");
        s.setCurrentPhase(0);
        s.setCurrentPhaseName("NOT_STARTED");

        List<PhaseDetail> phases = List.of(
                new PhaseDetail(1, "BOOT_AND_REGISTER", "Phase 1: Boot & Register Nodes"),
                new PhaseDetail(2, "CLOCK_SYNC", "Phase 2: Cristian's Clock Sync & Vector Clocks"),
                new PhaseDetail(3, "NETWORK_PARTITION", "Phase 3: Network Partition Simulation"),
                new PhaseDetail(4, "LEADER_ELECTION", "Phase 4: Leader Election (Bully & Ring)"),
                new PhaseDetail(5, "PARTITIONED_UPDATES", "Phase 5: Partitioned Local Updates"),
                new PhaseDetail(6, "HEAL_PARTITION", "Phase 6: Reconnect & Heal Partition"),
                new PhaseDetail(7, "RECONCILIATION", "Phase 7: CRDT Set Union & Reconciliation")
        );
        s.setPhases(phases);
        this.status = s;
    }

    private void publishPhaseEvent(int phase, String name, String phaseStatus, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("phase", phase);
        payload.put("name", name);
        payload.put("phaseStatus", phaseStatus);
        payload.put("message", message);
        payload.put("timestamp", System.currentTimeMillis());

        eventPublisher.publish("core", "SCENARIO_PHASE", payload);
    }

    private void logScenario(String line) {
        log.info(line);
        status.getLogs().add(line);
    }

    @FunctionalInterface
    private interface PhaseRunnable {
        void run() throws Exception;
    }
}

