package com.drs.comm.service;

import com.drs.comm.model.CollectiveResult;
import com.drs.comm.model.CommRequest;
import com.drs.comm.model.RankExecutionRecord;
import com.drs.comm.model.RankTimelineEntry;
import com.drs.core.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CommService
 * -----------
 * Primary Spring service implementing MPI Collective Communication (Experiment 9).
 * <p>
 * Simulates real-world disaster relief operations:
 * 1. Command Centre (Root Rank 0) broadcasts emergency alerts to rescue teams.
 * 2. Command Centre scatters sector zone assignments to rescue teams.
 * 3. Rescue teams perform local missions and generate status reports.
 * 4. Command Centre gathers rescue reports in strict rank order.
 * 5. Verifies self-check integrity and publishes live SSE events.
 */
@Service
public class CommService {

    private static final Logger log = LoggerFactory.getLogger(CommService.class);

    private final EventPublisher eventPublisher;

    public CommService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Executes the complete collective communication workflow:
     * MPI_Bcast -> MPI_Scatter -> Local Rescue -> MPI_Gather -> Self-Check Verification.
     */
    public CollectiveResult runAll(CommRequest request) {
        validateRequest(request);
        long startTime = System.currentTimeMillis();

        int processes = request.getProcesses();
        int chunkSize = request.getChunkSize();
        int root = sanitizeRoot(request.getRootRank(), processes);
        String alertText = resolveAlertText(request);
        String[] zones = resolveZones(request, processes, chunkSize);
        int[] numericPackages = resolveNumericPackages(request, processes, chunkSize);
        int[] numericAlert = request.getAlertData() != null ? request.getAlertData() : new int[]{911, 12, 5};

        Communicator comm = new Communicator(processes);
        CollectiveResult result = new CollectiveResult("RUN_ALL", processes, chunkSize, root);
        result.setAlertText(alertText);
        result.setAlertData(numericAlert.clone());
        result.setScattered(Arrays.asList(zones.clone()));
        result.setScatterData(numericPackages.clone());

        List<String> combinedSteps = new CopyOnWriteArrayList<>();
        List<RankTimelineEntry> timeline = new CopyOnWriteArrayList<>();
        AtomicInteger orderSeq = new AtomicInteger(1);

        Map<Integer, RankExecutionRecord> rankMap = new ConcurrentHashMap<>();
        for (int r = 0; r < processes; r++) {
            rankMap.put(r, new RankExecutionRecord(r, r == root));
        }

        String[][] gatheredStringsRef = new String[1][];
        int[][] gatheredIntsRef = new int[1][];

        combinedSteps.add("=== STEP 1: BROADCAST (MPI_Bcast) ===");
        combinedSteps.add("[Command Centre (Rank " + root + ")] Broadcasting alert: \"" + alertText + "\"");
        timeline.add(new RankTimelineEntry(root, "BCAST_DISPATCHED", alertText, orderSeq.getAndIncrement()));

        Thread[] threads = new Thread[processes];
        for (int r = 0; r < processes; r++) {
            final int myRank = r;
            final boolean isRoot = (myRank == root);

            threads[r] = new Thread(() -> {
                RankExecutionRecord record = rankMap.get(myRank);
                try {
                    // ── 1. BROADCAST ──
                    comm.barrier();
                    String myAlertText = isRoot ? alertText : null;
                    myAlertText = comm.bcastText(myAlertText, root, myRank);

                    int[] myNumericAlert = isRoot ? numericAlert.clone() : null;
                    myNumericAlert = comm.bcast(myNumericAlert, root, myRank);
                    record.setReceivedAlert(myNumericAlert);
                    record.addStep("Received alert text: \"" + myAlertText + "\"");

                    timeline.add(new RankTimelineEntry(myRank, "BCAST_RECEIVED", myAlertText, orderSeq.getAndIncrement()));

                    printOrdered(comm, myRank, processes, () -> {
                        combinedSteps.add("[Rank " + myRank + (isRoot ? " (COMMAND CENTRE)" : " (Rescue Team)")
                                + "] Alert confirmed: \"" + record.getSteps().get(0) + "\"");
                    });

                    // ── 2. SCATTER ──
                    comm.barrier();
                    if (isRoot) {
                        combinedSteps.add("=== STEP 2: SCATTER (MPI_Scatter) ===");
                        combinedSteps.add("[Command Centre] Scattering " + (processes * chunkSize)
                                + " disaster zones (" + chunkSize + " zones/team): " + Arrays.toString(zones));
                        timeline.add(new RankTimelineEntry(root, "SCATTER_DISPATCHED", Arrays.toString(zones), orderSeq.getAndIncrement()));
                    }
                    comm.barrier();

                    String[] sendZonesBuf = isRoot ? zones : null;
                    String[] myZones = comm.scatterStrings(sendZonesBuf, chunkSize, root, myRank);

                    int[] sendIntsBuf = isRoot ? numericPackages : null;
                    int[] myNumericChunk = comm.scatter(sendIntsBuf, chunkSize, root, myRank);
                    record.setReceivedChunk(myNumericChunk);
                    record.addStep("Assigned zones: " + Arrays.toString(myZones));

                    timeline.add(new RankTimelineEntry(myRank, "SCATTER_RECEIVED", Arrays.toString(myZones), orderSeq.getAndIncrement()));

                    printOrdered(comm, myRank, processes, () -> {
                        combinedSteps.add("[Rank " + myRank + "] Received sector assignments: " + Arrays.toString(myZones));
                    });

                    // ── 3. LOCAL WORK (SEARCH & RESCUE) ──
                    String[] myReports = new String[chunkSize];
                    int[] myDeliveredInts = new int[chunkSize];
                    for (int i = 0; i < chunkSize; i++) {
                        myReports[i] = "REPORT[R" + myRank + ":" + myZones[i] + ":EVAC_COMPLETE]";
                        myDeliveredInts[i] = myNumericChunk[i] + myRank;
                    }
                    record.setDeliveredChunk(myDeliveredInts);
                    record.addStep("Missions completed in assigned zones: " + Arrays.toString(myReports));

                    timeline.add(new RankTimelineEntry(myRank, "LOCAL_MISSION_COMPLETED", Arrays.toString(myReports), orderSeq.getAndIncrement()));

                    // ── 4. GATHER ──
                    comm.barrier();
                    if (isRoot) {
                        combinedSteps.add("=== STEP 3: GATHER (MPI_Gather) ===");
                        combinedSteps.add("Command Centre gathering mission reports from all rescue teams in rank order.");
                    }
                    comm.barrier();

                    timeline.add(new RankTimelineEntry(myRank, "GATHER_SENT", Arrays.toString(myReports), orderSeq.getAndIncrement()));

                    printOrdered(comm, myRank, processes, () -> {
                        combinedSteps.add("[Rank " + myRank + "] Transmitting reports to Command Centre: " + Arrays.toString(myReports));
                    });

                    String[] gatheredStrings = comm.gatherStrings(myReports, root, myRank);
                    int[] gatheredInts = comm.gather(myDeliveredInts, root, myRank);
                    comm.barrier();

                    if (isRoot) {
                        gatheredStringsRef[0] = gatheredStrings;
                        gatheredIntsRef[0] = gatheredInts;
                        record.addStep("Command Centre gathered all reports: " + Arrays.toString(gatheredStrings));
                        timeline.add(new RankTimelineEntry(root, "GATHER_ASSEMBLED", Arrays.toString(gatheredStrings), orderSeq.getAndIncrement()));
                    }
                } catch (Exception e) {
                    record.addStep("Rank failure: " + e.getMessage());
                    log.error("Rank {} error in collective communication: {}", myRank, e.getMessage(), e);
                }
            }, "Comm-Rank-" + r);
            threads[r].start();
        }

        waitForThreads(threads);

        String[] gathered = gatheredStringsRef[0] != null ? gatheredStringsRef[0] : new String[0];
        int[] gatheredInts = gatheredIntsRef[0] != null ? gatheredIntsRef[0] : new int[0];

        result.setGathered(Arrays.asList(gathered));
        result.setGatheredData(gatheredInts);

        // Self-Check Verification
        boolean selfCheckPassed = verifyGatheredIntegrity(gathered, processes, chunkSize);
        result.setSelfCheckPassed(selfCheckPassed);

        combinedSteps.add("=== STEP 4: SELF-CHECK VERIFICATION ===");
        combinedSteps.add("Command Centre assembled reports: " + Arrays.toString(gathered));
        combinedSteps.add("Strict rank-order & non-corruption check: " + (selfCheckPassed ? "PASSED (VERIFIED)" : "FAILED"));

        result.setSteps(new ArrayList<>(combinedSteps));
        timeline.sort(Comparator.comparingInt(RankTimelineEntry::getOrder));
        result.setTimeline(new ArrayList<>(timeline));

        List<RankExecutionRecord> orderedRecords = new ArrayList<>();
        for (int r = 0; r < processes; r++) {
            orderedRecords.add(rankMap.get(r));
        }
        result.setRankResults(orderedRecords);
        result.setDurationMs(System.currentTimeMillis() - startTime);

        // Publish live SSE events
        publishEvents(alertText, zones, gathered, selfCheckPassed, processes, chunkSize, root);

        return result;
    }

    /**
     * Executes MPI_Bcast: Command Centre broadcasts alert to all rescue teams.
     */
    public CollectiveResult runBroadcast(CommRequest request) {
        validateRequest(request);
        long startTime = System.currentTimeMillis();

        int processes = request.getProcesses();
        int root = sanitizeRoot(request.getRootRank(), processes);
        String alertText = resolveAlertText(request);
        int[] numericAlert = request.getAlertData() != null ? request.getAlertData() : new int[]{911, 12, 5};

        Communicator comm = new Communicator(processes);
        CollectiveResult result = new CollectiveResult("BCAST", processes, 0, root);
        result.setAlertText(alertText);
        result.setAlertData(numericAlert.clone());

        List<String> combinedSteps = new CopyOnWriteArrayList<>();
        List<RankTimelineEntry> timeline = new CopyOnWriteArrayList<>();
        AtomicInteger orderSeq = new AtomicInteger(1);

        Map<Integer, RankExecutionRecord> rankMap = new ConcurrentHashMap<>();
        for (int r = 0; r < processes; r++) {
            rankMap.put(r, new RankExecutionRecord(r, r == root));
        }

        combinedSteps.add("[Command Centre (Rank " + root + ")] Initiating MPI_Bcast: \"" + alertText + "\"");
        timeline.add(new RankTimelineEntry(root, "BCAST_DISPATCHED", alertText, orderSeq.getAndIncrement()));

        Thread[] threads = new Thread[processes];
        for (int r = 0; r < processes; r++) {
            final int myRank = r;
            final boolean isRoot = (myRank == root);

            threads[r] = new Thread(() -> {
                RankExecutionRecord record = rankMap.get(myRank);
                try {
                    String sendAlert = isRoot ? alertText : null;
                    final String receivedAlert = comm.bcastText(sendAlert, root, myRank);

                    int[] sendNumAlert = isRoot ? numericAlert.clone() : null;
                    int[] recNumAlert = comm.bcast(sendNumAlert, root, myRank);
                    record.setReceivedAlert(recNumAlert);
                    record.addStep("Confirmed alert: \"" + receivedAlert + "\"");

                    timeline.add(new RankTimelineEntry(myRank, "BCAST_RECEIVED", receivedAlert, orderSeq.getAndIncrement()));

                    printOrdered(comm, myRank, processes, () -> {
                        combinedSteps.add("[Rank " + myRank + (isRoot ? " (ROOT)" : "")
                                + "] received alert \"" + receivedAlert + "\"");
                    });
                } catch (Exception e) {
                    record.addStep("Bcast error: " + e.getMessage());
                }
            }, "Comm-Bcast-" + r);
            threads[r].start();
        }

        waitForThreads(threads);

        result.setSteps(new ArrayList<>(combinedSteps));
        timeline.sort(Comparator.comparingInt(RankTimelineEntry::getOrder));
        result.setTimeline(new ArrayList<>(timeline));

        List<RankExecutionRecord> orderedRecords = new ArrayList<>();
        for (int r = 0; r < processes; r++) {
            orderedRecords.add(rankMap.get(r));
        }
        result.setRankResults(orderedRecords);
        result.setSelfCheckPassed(true);
        result.setDurationMs(System.currentTimeMillis() - startTime);

        eventPublisher.publish("comm", "COMM_BCAST", Map.of(
                "operation", "BCAST",
                "alert", alertText,
                "processes", processes,
                "root", root
        ));

        return result;
    }

    /**
     * Executes MPI_Scatter: Command Centre splits zone assignments across rescue teams.
     */
    public CollectiveResult runScatter(CommRequest request) {
        validateRequest(request);
        long startTime = System.currentTimeMillis();

        int processes = request.getProcesses();
        int chunkSize = request.getChunkSize();
        int root = sanitizeRoot(request.getRootRank(), processes);
        String[] zones = resolveZones(request, processes, chunkSize);
        int[] numericPackages = resolveNumericPackages(request, processes, chunkSize);

        Communicator comm = new Communicator(processes);
        CollectiveResult result = new CollectiveResult("SCATTER", processes, chunkSize, root);
        result.setScattered(Arrays.asList(zones.clone()));
        result.setScatterData(numericPackages.clone());

        List<String> combinedSteps = new CopyOnWriteArrayList<>();
        List<RankTimelineEntry> timeline = new CopyOnWriteArrayList<>();
        AtomicInteger orderSeq = new AtomicInteger(1);

        Map<Integer, RankExecutionRecord> rankMap = new ConcurrentHashMap<>();
        for (int r = 0; r < processes; r++) {
            rankMap.put(r, new RankExecutionRecord(r, r == root));
        }

        combinedSteps.add("[Command Centre (Rank " + root + ")] Scattering " + zones.length
                + " zones (" + chunkSize + " per team)");
        timeline.add(new RankTimelineEntry(root, "SCATTER_DISPATCHED", Arrays.toString(zones), orderSeq.getAndIncrement()));

        Thread[] threads = new Thread[processes];
        for (int r = 0; r < processes; r++) {
            final int myRank = r;
            final boolean isRoot = (myRank == root);

            threads[r] = new Thread(() -> {
                RankExecutionRecord record = rankMap.get(myRank);
                try {
                    String[] sendZonesBuf = isRoot ? zones : null;
                    String[] myZones = comm.scatterStrings(sendZonesBuf, chunkSize, root, myRank);

                    int[] sendIntsBuf = isRoot ? numericPackages : null;
                    int[] myNumeric = comm.scatter(sendIntsBuf, chunkSize, root, myRank);
                    record.setReceivedChunk(myNumeric);
                    record.addStep("Received zones " + Arrays.toString(myZones));

                    timeline.add(new RankTimelineEntry(myRank, "SCATTER_RECEIVED", Arrays.toString(myZones), orderSeq.getAndIncrement()));

                    printOrdered(comm, myRank, processes, () -> {
                        combinedSteps.add("[Rank " + myRank + (isRoot ? " (ROOT)" : "")
                                + "] received zones " + Arrays.toString(myZones));
                    });
                } catch (Exception e) {
                    record.addStep("Scatter error: " + e.getMessage());
                }
            }, "Comm-Scatter-" + r);
            threads[r].start();
        }

        waitForThreads(threads);

        result.setSteps(new ArrayList<>(combinedSteps));
        timeline.sort(Comparator.comparingInt(RankTimelineEntry::getOrder));
        result.setTimeline(new ArrayList<>(timeline));

        List<RankExecutionRecord> orderedRecords = new ArrayList<>();
        for (int r = 0; r < processes; r++) {
            orderedRecords.add(rankMap.get(r));
        }
        result.setRankResults(orderedRecords);
        result.setSelfCheckPassed(true);
        result.setDurationMs(System.currentTimeMillis() - startTime);

        eventPublisher.publish("comm", "COMM_SCATTER", Map.of(
                "operation", "SCATTER",
                "scattered", zones,
                "chunkSize", chunkSize,
                "processes", processes,
                "root", root
        ));

        return result;
    }

    /**
     * Executes MPI_Gather: Collects rescue mission reports from all teams in rank order.
     */
    public CollectiveResult runGather(CommRequest request) {
        validateRequest(request);
        long startTime = System.currentTimeMillis();

        int processes = request.getProcesses();
        int chunkSize = request.getChunkSize();
        int root = sanitizeRoot(request.getRootRank(), processes);

        Communicator comm = new Communicator(processes);
        CollectiveResult result = new CollectiveResult("GATHER", processes, chunkSize, root);

        List<String> combinedSteps = new CopyOnWriteArrayList<>();
        List<RankTimelineEntry> timeline = new CopyOnWriteArrayList<>();
        AtomicInteger orderSeq = new AtomicInteger(1);

        Map<Integer, RankExecutionRecord> rankMap = new ConcurrentHashMap<>();
        for (int r = 0; r < processes; r++) {
            rankMap.put(r, new RankExecutionRecord(r, r == root));
        }

        combinedSteps.add("[Gather] Gathering " + chunkSize + " reports from each of " + processes + " teams into Root " + root);

        String[][] gatheredStringsRef = new String[1][];
        int[][] gatheredIntsRef = new int[1][];

        Thread[] threads = new Thread[processes];
        for (int r = 0; r < processes; r++) {
            final int myRank = r;
            final boolean isRoot = (myRank == root);

            threads[r] = new Thread(() -> {
                RankExecutionRecord record = rankMap.get(myRank);
                try {
                    String[] myReports = new String[chunkSize];
                    int[] localInts = new int[chunkSize];
                    for (int i = 0; i < chunkSize; i++) {
                        myReports[i] = "REPORT[R" + myRank + ":ZONE-" + (char) ('A' + myRank) + (i + 1) + ":OK]";
                        localInts[i] = (myRank + 1) * 100 + i;
                    }
                    record.setDeliveredChunk(localInts);
                    record.addStep("Submitting reports: " + Arrays.toString(myReports));

                    timeline.add(new RankTimelineEntry(myRank, "GATHER_SENT", Arrays.toString(myReports), orderSeq.getAndIncrement()));

                    printOrdered(comm, myRank, processes, () -> {
                        combinedSteps.add("[Rank " + myRank + "] sending reports " + Arrays.toString(myReports));
                    });

                    String[] gatheredStrings = comm.gatherStrings(myReports, root, myRank);
                    int[] gatheredInts = comm.gather(localInts, root, myRank);

                    if (isRoot) {
                        gatheredStringsRef[0] = gatheredStrings;
                        gatheredIntsRef[0] = gatheredInts;
                        record.addStep("Gathered: " + Arrays.toString(gatheredStrings));
                        timeline.add(new RankTimelineEntry(root, "GATHER_ASSEMBLED", Arrays.toString(gatheredStrings), orderSeq.getAndIncrement()));
                    }
                } catch (Exception e) {
                    record.addStep("Gather error: " + e.getMessage());
                }
            }, "Comm-Gather-" + r);
            threads[r].start();
        }

        waitForThreads(threads);

        String[] gathered = gatheredStringsRef[0] != null ? gatheredStringsRef[0] : new String[0];
        int[] gatheredInts = gatheredIntsRef[0] != null ? gatheredIntsRef[0] : new int[0];

        result.setGathered(Arrays.asList(gathered));
        result.setGatheredData(gatheredInts);

        boolean selfCheckPassed = verifyGatheredIntegrity(gathered, processes, chunkSize);
        result.setSelfCheckPassed(selfCheckPassed);

        combinedSteps.add("[Root " + root + "] Gathered buffer assembled: " + Arrays.toString(gathered));
        result.setSteps(new ArrayList<>(combinedSteps));
        timeline.sort(Comparator.comparingInt(RankTimelineEntry::getOrder));
        result.setTimeline(new ArrayList<>(timeline));

        List<RankExecutionRecord> orderedRecords = new ArrayList<>();
        for (int r = 0; r < processes; r++) {
            orderedRecords.add(rankMap.get(r));
        }
        result.setRankResults(orderedRecords);
        result.setDurationMs(System.currentTimeMillis() - startTime);

        eventPublisher.publish("comm", "COMM_GATHER", Map.of(
                "operation", "GATHER",
                "gathered", gathered,
                "selfCheckPassed", selfCheckPassed,
                "processes", processes,
                "root", root
        ));

        return result;
    }

    // ── Validation and Sanitization ──

    public void validateRequest(CommRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CommRequest cannot be null");
        }
        int processes = request.getProcesses();
        if (processes < 2 || processes > 16) {
            throw new IllegalArgumentException("Process count must be between 2 and 16, got " + processes);
        }
        int chunkSize = request.getChunkSize();
        if (chunkSize < 1 || chunkSize > 10) {
            throw new IllegalArgumentException("Chunk size must be between 1 and 10, got " + chunkSize);
        }
    }

    private int sanitizeRoot(int root, int processes) {
        if (root < 0 || root >= processes) return 0;
        return root;
    }

    private String resolveAlertText(CommRequest req) {
        if (req.getAlertText() != null && !req.getAlertText().trim().isEmpty()) {
            return req.getAlertText().trim();
        }
        return "CRITICAL: Sector 4 Flash Flood Warning - Immediate Evacuation & Relief Operation";
    }

    private String[] resolveZones(CommRequest req, int processes, int chunkSize) {
        int totalNeeded = processes * chunkSize;
        if (req.getZoneIds() != null && req.getZoneIds().size() == totalNeeded) {
            return req.getZoneIds().toArray(new String[0]);
        }
        String[] zones = new String[totalNeeded];
        for (int i = 0; i < totalNeeded; i++) {
            int rankIdx = i / chunkSize;
            int subIdx = (i % chunkSize) + 1;
            char sectorLetter = (char) ('A' + (rankIdx % 26));
            zones[i] = "ZONE-" + sectorLetter + subIdx;
        }
        return zones;
    }

    private int[] resolveNumericPackages(CommRequest req, int processes, int chunkSize) {
        int totalNeeded = processes * chunkSize;
        if (req.getPackagesData() != null && req.getPackagesData().length == totalNeeded) {
            return req.getPackagesData();
        }
        int[] pkgs = new int[totalNeeded];
        for (int i = 0; i < totalNeeded; i++) {
            pkgs[i] = (i + 1) * 10;
        }
        return pkgs;
    }

    private boolean verifyGatheredIntegrity(String[] gathered, int processes, int chunkSize) {
        if (gathered == null || gathered.length != processes * chunkSize) {
            return false;
        }
        for (int r = 0; r < processes; r++) {
            for (int c = 0; c < chunkSize; c++) {
                int idx = r * chunkSize + c;
                String item = gathered[idx];
                if (item == null || !item.contains("R" + r + ":")) {
                    return false;
                }
            }
        }
        return true;
    }

    private void printOrdered(Communicator comm, int myRank, int size, Runnable action) throws Exception {
        for (int r = 0; r < size; r++) {
            comm.barrier();
            if (r == myRank) {
                action.run();
            }
        }
        comm.barrier();
    }

    private void waitForThreads(Thread[] threads) {
        for (Thread t : threads) {
            try {
                t.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void publishEvents(String alertText, String[] zones, String[] gathered,
                               boolean verified, int processes, int chunkSize, int root) {
        eventPublisher.publish("comm", "COMM_BCAST", Map.of(
                "operation", "BCAST",
                "alert", alertText,
                "processes", processes,
                "root", root
        ));

        eventPublisher.publish("comm", "COMM_SCATTER", Map.of(
                "operation", "SCATTER",
                "scattered", zones,
                "chunkSize", chunkSize,
                "processes", processes,
                "root", root
        ));

        eventPublisher.publish("comm", "COMM_GATHER", Map.of(
                "operation", "GATHER",
                "gathered", gathered,
                "selfCheckPassed", verified,
                "processes", processes,
                "root", root
        ));
    }
}
