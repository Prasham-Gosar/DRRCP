package com.drs.comm.service;

import com.drs.comm.model.CollectiveResult;
import com.drs.comm.model.CommRequest;
import com.drs.comm.model.RankExecutionRecord;
import com.drs.core.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Spring service port of Experiment 9: Collective Communication (Broadcast, Scatter, Gather).
 * <p>
 * Replaces console System.out prints with structured {@link CollectiveResult} and
 * per-rank {@link RankExecutionRecord} traces, broadcasting live events through {@link EventPublisher}.
 */
@Service
public class CollectiveCommService {

    private static final Logger log = LoggerFactory.getLogger(CollectiveCommService.class);

    private final EventPublisher eventPublisher;

    public CollectiveCommService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Executes the full Experiment 9 story:
     * 1. BROADCAST: Command centre (root) sends emergency alert to all rescue teams.
     * 2. SCATTER: Command centre splits supply packages so each team receives its share.
     * 3. LOCAL WORK: Each team delivers supplies in its disaster zone (+rank bonus).
     * 4. GATHER: Each team reports delivered supplies; command centre gathers reports in rank order.
     * 5. VERIFY: Verifies gathered checksum and rank-order preservation.
     */
    public CollectiveResult runAll(CommRequest request) {
        long startTime = System.currentTimeMillis();
        int numProcesses = sanitizeProcesses(request.getNumProcesses());
        int chunkSize = sanitizeChunkSize(request.getChunkSize());
        int root = sanitizeRoot(request.getRootRank(), numProcesses);

        int[] alert = request.getAlertData() != null ? request.getAlertData() : new int[]{911, 12, 5};
        int[] allPackages = request.getPackagesData();
        if (allPackages == null || allPackages.length != numProcesses * chunkSize) {
            allPackages = new int[numProcesses * chunkSize];
            for (int i = 0; i < allPackages.length; i++) {
                allPackages[i] = (i + 1) * 10; // 10, 20, 30, ...
            }
        }

        Communicator comm = new Communicator(numProcesses);
        CollectiveResult result = new CollectiveResult("RUN_ALL", numProcesses, chunkSize, root);
        result.setAlertData(alert.clone());
        result.setScatterData(allPackages.clone());

        List<String> combinedSteps = new CopyOnWriteArrayList<>();
        Map<Integer, RankExecutionRecord> rankMap = new ConcurrentHashMap<>();
        for (int r = 0; r < numProcesses; r++) {
            rankMap.put(r, new RankExecutionRecord(r, r == root));
        }

        final int[] finalAllPackages = allPackages;
        int[][] gatheredRef = new int[1][];

        combinedSteps.add("=== STEP 1: BROADCAST (MPI_Bcast) ===");
        combinedSteps.add("[Root " + root + "] Broadcast emergency alert " + Arrays.toString(alert)
                + " to " + numProcesses + " rescue team ranks.");

        Thread[] threads = new Thread[numProcesses];
        for (int r = 0; r < numProcesses; r++) {
            final int myRank = r;
            final boolean isRoot = (myRank == root);

            threads[r] = new Thread(() -> {
                RankExecutionRecord record = rankMap.get(myRank);
                try {
                    // ── 1. BROADCAST ──
                    comm.barrier();
                    int[] myAlert = isRoot ? alert.clone() : null;
                    myAlert = comm.bcast(myAlert, root, myRank);
                    record.setReceivedAlert(myAlert);
                    record.addStep("Received broadcast alert: " + Arrays.toString(myAlert));

                    printOrdered(comm, myRank, numProcesses, () -> {
                        combinedSteps.add("[Rank " + myRank + (isRoot ? " (ROOT)" : "")
                                + "] received alert " + Arrays.toString(record.getReceivedAlert()));
                    });

                    // ── 2. SCATTER ──
                    comm.barrier();
                    if (isRoot) {
                        combinedSteps.add("=== STEP 2: SCATTER (MPI_Scatter) ===");
                        combinedSteps.add("[Root " + root + "] Holds supply packages "
                                + Arrays.toString(finalAllPackages) + " and splits into "
                                + numProcesses + " chunks of size " + chunkSize + ".");
                    }
                    comm.barrier();

                    int[] sendBuf = isRoot ? finalAllPackages : null;
                    int[] myChunk = comm.scatter(sendBuf, chunkSize, root, myRank);
                    record.setReceivedChunk(myChunk);
                    record.addStep("Received scatter chunk: " + Arrays.toString(myChunk));

                    printOrdered(comm, myRank, numProcesses, () -> {
                        combinedSteps.add("[Rank " + myRank + (isRoot ? " (ROOT)" : "")
                                + "] received chunk " + Arrays.toString(record.getReceivedChunk()));
                    });

                    // ── 3. LOCAL WORK ──
                    int[] delivered = new int[chunkSize];
                    for (int i = 0; i < chunkSize; i++) {
                        delivered[i] = myChunk[i] + myRank;
                    }
                    record.setDeliveredChunk(delivered);
                    record.addStep("Delivered supplies with rank bonus (+" + myRank + "): " + Arrays.toString(delivered));

                    // ── 4. GATHER ──
                    comm.barrier();
                    if (isRoot) {
                        combinedSteps.add("=== STEP 3: GATHER (MPI_Gather) ===");
                        combinedSteps.add("All rescue teams report local deliveries; root gathers in rank order.");
                    }
                    comm.barrier();

                    printOrdered(comm, myRank, numProcesses, () -> {
                        combinedSteps.add("[Rank " + myRank + "] sending delivery report " + Arrays.toString(delivered));
                    });

                    int[] gathered = comm.gather(delivered, root, myRank);
                    comm.barrier();

                    if (isRoot) {
                        gatheredRef[0] = gathered;
                        record.addStep("Assembled gathered buffer: " + Arrays.toString(gathered));
                    }
                } catch (Exception e) {
                    record.addStep("Execution failed: " + e.getMessage());
                    log.error("Rank {} failed in collective communication: {}", myRank, e.getMessage());
                }
            }, "Rank-" + r);
            threads[r].start();
        }

        waitForThreads(threads);

        int[] gathered = gatheredRef[0];
        result.setGatheredData(gathered);

        // Verification
        boolean verified = false;
        if (gathered != null && gathered.length == numProcesses * chunkSize) {
            verified = true;
            for (int i = 0; i < gathered.length; i++) {
                if (gathered[i] != finalAllPackages[i] + (i / chunkSize)) {
                    verified = false;
                    break;
                }
            }
        }
        result.setVerified(verified);

        combinedSteps.add("=== STEP 4: VERIFICATION ===");
        combinedSteps.add("Root gathered buffer: " + Arrays.toString(gathered));
        combinedSteps.add("Rank order and delivery integrity: " + (verified ? "PASSED" : "FAILED"));

        result.setSteps(new ArrayList<>(combinedSteps));
        List<RankExecutionRecord> orderedRankList = new ArrayList<>();
        for (int r = 0; r < numProcesses; r++) {
            orderedRankList.add(rankMap.get(r));
        }
        result.setRankResults(orderedRankList);
        result.setDurationMs(System.currentTimeMillis() - startTime);

        // Publish live SSE events
        eventPublisher.publish("comm", "COMM_BCAST", Map.of(
                "operation", "BCAST",
                "alert", alert,
                "ranks", numProcesses,
                "root", root
        ));

        eventPublisher.publish("comm", "COMM_SCATTER", Map.of(
                "operation", "SCATTER",
                "packages", allPackages,
                "chunkSize", chunkSize,
                "ranks", numProcesses
        ));

        eventPublisher.publish("comm", "COMM_GATHER", Map.of(
                "operation", "GATHER",
                "gathered", gathered != null ? gathered : new int[0],
                "verified", verified,
                "ranks", numProcesses
        ));

        return result;
    }

    /**
     * Executes MPI_Bcast only across threads.
     */
    public CollectiveResult runBroadcast(CommRequest request) {
        long startTime = System.currentTimeMillis();
        int numProcesses = sanitizeProcesses(request.getNumProcesses());
        int root = sanitizeRoot(request.getRootRank(), numProcesses);
        int[] alert = request.getAlertData() != null ? request.getAlertData() : new int[]{911, 12, 5};

        Communicator comm = new Communicator(numProcesses);
        CollectiveResult result = new CollectiveResult("BCAST", numProcesses, 0, root);
        result.setAlertData(alert.clone());

        List<String> combinedSteps = new CopyOnWriteArrayList<>();
        Map<Integer, RankExecutionRecord> rankMap = new ConcurrentHashMap<>();
        for (int r = 0; r < numProcesses; r++) {
            rankMap.put(r, new RankExecutionRecord(r, r == root));
        }

        combinedSteps.add("[Root " + root + "] Initiating MPI_Bcast with alert " + Arrays.toString(alert));

        Thread[] threads = new Thread[numProcesses];
        for (int r = 0; r < numProcesses; r++) {
            final int myRank = r;
            final boolean isRoot = (myRank == root);

            threads[r] = new Thread(() -> {
                RankExecutionRecord record = rankMap.get(myRank);
                try {
                    int[] myAlert = isRoot ? alert.clone() : null;
                    myAlert = comm.bcast(myAlert, root, myRank);
                    record.setReceivedAlert(myAlert);
                    record.addStep("Received alert " + Arrays.toString(myAlert));

                    printOrdered(comm, myRank, numProcesses, () -> {
                        combinedSteps.add("[Rank " + myRank + (isRoot ? " (ROOT)" : "")
                                + "] received alert " + Arrays.toString(record.getReceivedAlert()));
                    });
                } catch (Exception e) {
                    record.addStep("Bcast error: " + e.getMessage());
                }
            }, "Rank-Bcast-" + r);
            threads[r].start();
        }

        waitForThreads(threads);

        result.setSteps(new ArrayList<>(combinedSteps));
        List<RankExecutionRecord> orderedRankList = new ArrayList<>();
        for (int r = 0; r < numProcesses; r++) {
            orderedRankList.add(rankMap.get(r));
        }
        result.setRankResults(orderedRankList);
        result.setDurationMs(System.currentTimeMillis() - startTime);
        result.setVerified(true);

        eventPublisher.publish("comm", "COMM_BCAST", Map.of(
                "operation", "BCAST",
                "alert", alert,
                "ranks", numProcesses,
                "root", root
        ));

        return result;
    }

    /**
     * Executes MPI_Scatter only across threads.
     */
    public CollectiveResult runScatter(CommRequest request) {
        long startTime = System.currentTimeMillis();
        int numProcesses = sanitizeProcesses(request.getNumProcesses());
        int chunkSize = sanitizeChunkSize(request.getChunkSize());
        int root = sanitizeRoot(request.getRootRank(), numProcesses);

        int[] allPackages = request.getPackagesData();
        if (allPackages == null || allPackages.length != numProcesses * chunkSize) {
            allPackages = new int[numProcesses * chunkSize];
            for (int i = 0; i < allPackages.length; i++) {
                allPackages[i] = (i + 1) * 10;
            }
        }

        Communicator comm = new Communicator(numProcesses);
        CollectiveResult result = new CollectiveResult("SCATTER", numProcesses, chunkSize, root);
        result.setScatterData(allPackages.clone());

        List<String> combinedSteps = new CopyOnWriteArrayList<>();
        Map<Integer, RankExecutionRecord> rankMap = new ConcurrentHashMap<>();
        for (int r = 0; r < numProcesses; r++) {
            rankMap.put(r, new RankExecutionRecord(r, r == root));
        }

        combinedSteps.add("[Root " + root + "] Scattering buffer " + Arrays.toString(allPackages)
                + " (" + chunkSize + " per rank)");

        final int[] finalAllPackages = allPackages;
        Thread[] threads = new Thread[numProcesses];
        for (int r = 0; r < numProcesses; r++) {
            final int myRank = r;
            final boolean isRoot = (myRank == root);

            threads[r] = new Thread(() -> {
                RankExecutionRecord record = rankMap.get(myRank);
                try {
                    int[] sendBuf = isRoot ? finalAllPackages : null;
                    int[] myChunk = comm.scatter(sendBuf, chunkSize, root, myRank);
                    record.setReceivedChunk(myChunk);
                    record.addStep("Received scatter chunk " + Arrays.toString(myChunk));

                    printOrdered(comm, myRank, numProcesses, () -> {
                        combinedSteps.add("[Rank " + myRank + (isRoot ? " (ROOT)" : "")
                                + "] received chunk " + Arrays.toString(record.getReceivedChunk()));
                    });
                } catch (Exception e) {
                    record.addStep("Scatter error: " + e.getMessage());
                }
            }, "Rank-Scatter-" + r);
            threads[r].start();
        }

        waitForThreads(threads);

        result.setSteps(new ArrayList<>(combinedSteps));
        List<RankExecutionRecord> orderedRankList = new ArrayList<>();
        for (int r = 0; r < numProcesses; r++) {
            orderedRankList.add(rankMap.get(r));
        }
        result.setRankResults(orderedRankList);
        result.setDurationMs(System.currentTimeMillis() - startTime);
        result.setVerified(true);

        eventPublisher.publish("comm", "COMM_SCATTER", Map.of(
                "operation", "SCATTER",
                "packages", allPackages,
                "chunkSize", chunkSize,
                "ranks", numProcesses
        ));

        return result;
    }

    /**
     * Executes MPI_Gather only across threads.
     */
    public CollectiveResult runGather(CommRequest request) {
        long startTime = System.currentTimeMillis();
        int numProcesses = sanitizeProcesses(request.getNumProcesses());
        int chunkSize = sanitizeChunkSize(request.getChunkSize());
        int root = sanitizeRoot(request.getRootRank(), numProcesses);

        Communicator comm = new Communicator(numProcesses);
        CollectiveResult result = new CollectiveResult("GATHER", numProcesses, chunkSize, root);

        List<String> combinedSteps = new CopyOnWriteArrayList<>();
        Map<Integer, RankExecutionRecord> rankMap = new ConcurrentHashMap<>();
        for (int r = 0; r < numProcesses; r++) {
            rankMap.put(r, new RankExecutionRecord(r, r == root));
        }

        combinedSteps.add("[Gather] Each rank sends " + chunkSize + " elements to Root " + root);

        int[][] gatheredRef = new int[1][];
        Thread[] threads = new Thread[numProcesses];
        for (int r = 0; r < numProcesses; r++) {
            final int myRank = r;
            final boolean isRoot = (myRank == root);

            threads[r] = new Thread(() -> {
                RankExecutionRecord record = rankMap.get(myRank);
                try {
                    int[] localData = new int[chunkSize];
                    for (int i = 0; i < chunkSize; i++) {
                        localData[i] = (myRank + 1) * 100 + i;
                    }
                    record.setDeliveredChunk(localData);
                    record.addStep("Sending local data " + Arrays.toString(localData));

                    printOrdered(comm, myRank, numProcesses, () -> {
                        combinedSteps.add("[Rank " + myRank + "] sending " + Arrays.toString(localData));
                    });

                    int[] gathered = comm.gather(localData, root, myRank);
                    if (isRoot) {
                        gatheredRef[0] = gathered;
                        record.addStep("Gathered: " + Arrays.toString(gathered));
                    }
                } catch (Exception e) {
                    record.addStep("Gather error: " + e.getMessage());
                }
            }, "Rank-Gather-" + r);
            threads[r].start();
        }

        waitForThreads(threads);

        int[] gathered = gatheredRef[0];
        result.setGatheredData(gathered);
        combinedSteps.add("[Root " + root + "] Gathered buffer: " + Arrays.toString(gathered));

        result.setSteps(new ArrayList<>(combinedSteps));
        List<RankExecutionRecord> orderedRankList = new ArrayList<>();
        for (int r = 0; r < numProcesses; r++) {
            orderedRankList.add(rankMap.get(r));
        }
        result.setRankResults(orderedRankList);
        result.setDurationMs(System.currentTimeMillis() - startTime);
        result.setVerified(gathered != null && gathered.length == numProcesses * chunkSize);

        eventPublisher.publish("comm", "COMM_GATHER", Map.of(
                "operation", "GATHER",
                "gathered", gathered != null ? gathered : new int[0],
                "ranks", numProcesses,
                "root", root
        ));

        return result;
    }

    // ── Helper synchronisation methods ──

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

    private int sanitizeProcesses(int num) {
        if (num <= 0) return 4;
        return Math.min(num, 16);
    }

    private int sanitizeChunkSize(int chunk) {
        if (chunk <= 0) return 2;
        return Math.min(chunk, 32);
    }

    private int sanitizeRoot(int root, int numProcesses) {
        if (root < 0 || root >= numProcesses) return 0;
        return root;
    }
}
