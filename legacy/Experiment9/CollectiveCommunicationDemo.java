import java.util.Arrays;

/*
 * EXPERIMENT 9 - Collective Communication: Broadcast, Scatter, Gather
 * Disaster Relief Coordination System
 *
 * Simulates MPI with Java threads (one thread = one rank) because no standard
 * MPI library is available for Java here. Same concepts, same call pattern:
 *
 *   MPI_Bcast   -> Communicator.bcast
 *   MPI_Scatter -> Communicator.scatter
 *   MPI_Gather  -> Communicator.gather
 *
 * Story: rank 0 is the Command Centre (the ROOT). The other ranks are rescue
 * teams working in different zones.
 *   1. BROADCAST : command centre sends the same emergency alert to all teams.
 *   2. SCATTER   : command centre splits a list of supply packages so each
 *                  team gets its own share.
 *   3. GATHER    : each team reports how many packages it delivered; the
 *                  command centre collects the reports in rank order.
 *
 * Usage:  java CollectiveCommunicationDemo [processes] [chunkSize]
 *         defaults: 4 processes, 2 elements per process
 */
public class CollectiveCommunicationDemo {

    private static final int ROOT = 0;

    public static void main(String[] args) throws Exception {

        int numProcesses = 4;
        int chunkSize = 2;
        try {
            if (args.length > 0) numProcesses = Integer.parseInt(args[0]);
            if (args.length > 1) chunkSize = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Usage: java CollectiveCommunicationDemo [processes] [chunkSize]  (positive integers)");
            return;
        }
        if (numProcesses < 1 || chunkSize < 1) {
            System.out.println("Error: processes and chunkSize must both be at least 1.");
            return;
        }

        System.out.println(" MPI COLLECTIVE COMMUNICATION (simulated with Java threads)");
        System.out.println(" Disaster Relief Coordination System");
        System.out.println(" Processes (ranks): " + numProcesses + " | Chunk size: " + chunkSize
                + " | Root: rank " + ROOT);
        System.out.println();

        final Communicator comm = new Communicator(numProcesses);   // our "MPI_COMM_WORLD"
        final int chunk = chunkSize;

        Thread[] ranks = new Thread[numProcesses];
        for (int r = 0; r < numProcesses; r++) {
            final int rank = r;
            ranks[r] = new Thread(() -> {
                try {
                    runRank(comm, rank, chunk);
                } catch (Exception e) {
                    System.out.println("[Rank " + rank + "] FAILED: " + e);
                    Runtime.getRuntime().halt(1);   // avoid other ranks hanging at a barrier
                }
            }, "Rank-" + r);
            ranks[r].start();
        }
        for (Thread t : ranks) {
            t.join();
        }
        System.out.println("Experiment 9 complete.");
    }

    // The code every rank runs (like the body of an MPI program's main()).
    private static void runRank(Communicator comm, int rank, int chunk) throws Exception {

        int size = comm.size();
        boolean isRoot = (rank == ROOT);

        // ================= 1. BROADCAST =================
        if (isRoot) {
            System.out.println("=== STEP 1: BROADCAST (MPI_Bcast) ===");
            System.out.println("Root rank " + ROOT + " sends the SAME alert to every rank.");
        }
        comm.barrier();

        // Only the root has the data to begin with; everyone else passes null.
        int[] alert = isRoot ? new int[]{911, 12, 5} : null;   // {alert code, zone, severity}
        alert = comm.bcast(alert, ROOT, rank);

        comm.printInRankOrder(rank, "[Rank " + rank + (isRoot ? " (ROOT)" : "") + "] received alert "
                + Arrays.toString(alert));

        // ================= 2. SCATTER =================
        if (isRoot) {
            System.out.println();
            System.out.println("=== STEP 2: SCATTER (MPI_Scatter) ===");
        }
        comm.barrier();

        int[] allPackages = null;
        if (isRoot) {
            // Data size is chosen from the process count, so it always divides evenly.
            allPackages = new int[size * chunk];
            for (int i = 0; i < allPackages.length; i++) {
                allPackages[i] = (i + 1) * 10;      // 10, 20, 30, ...
            }
            System.out.println("Root holds " + Arrays.toString(allPackages)
                    + " and splits it into " + size + " chunks of " + chunk + ".");
        }
        comm.barrier();

        int[] myChunk = comm.scatter(allPackages, chunk, ROOT, rank);

        comm.printInRankOrder(rank, "[Rank " + rank + "] received chunk " + Arrays.toString(myChunk));

        // Each rank does a bit of local work on its own chunk (delivers it: value + rank bonus).
        int[] delivered = new int[chunk];
        for (int i = 0; i < chunk; i++) {
            delivered[i] = myChunk[i] + rank;       // distinct result per rank, easy to verify
        }

        // ================= 3. GATHER =================
        if (isRoot) {
            System.out.println();
            System.out.println("=== STEP 3: GATHER (MPI_Gather) ===");
            System.out.println("Every rank sends its delivery report; root assembles them in rank order.");
        }
        comm.barrier();

        comm.printInRankOrder(rank, "[Rank " + rank + "] sending " + Arrays.toString(delivered));

        int[] gathered = comm.gather(delivered, ROOT, rank);

        comm.barrier();
        if (isRoot) {
            System.out.println("Root gathered buffer: " + Arrays.toString(gathered));

            // Self-check: gathering must be the inverse of scattering (plus each rank's +rank change).
            boolean ok = true;
            for (int i = 0; i < gathered.length; i++) {
                if (gathered[i] != allPackages[i] + (i / chunk)) {
                    ok = false;
                }
            }
            System.out.println("Verification (rank order preserved): " + (ok ? "PASSED" : "FAILED"));
            System.out.println();
        }
        comm.barrier();
    }
}
