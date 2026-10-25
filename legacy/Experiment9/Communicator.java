import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;

/*
 * Communicator
 * ------------
 * A tiny simulation of an MPI communicator (think MPI_COMM_WORLD).
 *
 * Real MPI starts N separate OS processes ("ranks") that exchange messages.
 * Java has no standard MPI binding, so here every "rank" is a Java THREAD and
 * the "network" is a grid of blocking queues:
 *
 *      mailbox[to][from]  = messages that rank 'from' sent to rank 'to'
 *
 * Communicator    : the group of ranks that take part in a communication.
 * Rank            : the id (0 .. size-1) of one process inside the communicator.
 * Root            : the one rank that owns the data (Bcast/Scatter) or
 *                   receives the result (Gather).
 * Collective call : an operation that EVERY rank in the communicator must
 *                   call (bcast, scatter, gather). Each rank calls the same
 *                   method; the 'root' argument decides who plays which part.
 */
public class Communicator {

    private final int size;
    private final BlockingQueue<int[]>[][] mailbox;
    private final CyclicBarrier barrier;

    @SuppressWarnings("unchecked")
    public Communicator(int size) {
        this.size = size;
        this.mailbox = new BlockingQueue[size][size];
        for (int to = 0; to < size; to++) {
            for (int from = 0; from < size; from++) {
                mailbox[to][from] = new LinkedBlockingQueue<>();
            }
        }
        this.barrier = new CyclicBarrier(size);
    }

    // Like MPI_Comm_size: how many ranks are in this communicator.
    public int size() {
        return size;
    }

    // ---------- point-to-point primitives (like MPI_Send / MPI_Recv) ----------

    private void send(int[] data, int from, int to) {
        mailbox[to][from].add(data.clone());   // clone = a real copy, like a network message
    }

    private int[] recv(int to, int from) throws InterruptedException {
        return mailbox[to][from].take();       // blocks until the message arrives
    }

    // Like MPI_Barrier: nobody continues until every rank has reached this line.
    public void barrier() throws Exception {
        barrier.await();
    }

    // ---------- MPI_Bcast ----------
    /*
     * Broadcast: the root sends the EXACT SAME array to every other rank.
     *   root  --same data-->  rank 1
     *         --same data-->  rank 2
     *         --same data-->  rank N-1
     *
     * Root passes its real data. All other ranks pass null (they have nothing
     * yet) and get the root's data back as the return value.
     */
    public int[] bcast(int[] data, int root, int myRank) throws InterruptedException {
        if (myRank == root) {
            for (int r = 0; r < size; r++) {
                if (r != root) {
                    send(data, root, r);
                }
            }
            return data;
        }
        return recv(myRank, root);
    }

    // ---------- MPI_Scatter ----------
    /*
     * Scatter: the root splits its array into 'size' equal chunks and sends
     * chunk i to rank i (root keeps chunk 0 if root == 0, i.e. its own chunk).
     *   [ c0 | c1 | c2 | c3 ]  -->  rank0 gets c0, rank1 gets c1, ...
     *
     * Different from Bcast: every rank gets a DIFFERENT piece, not a copy of all.
     * The array length must be size * chunkSize (checked here).
     */
    public int[] scatter(int[] sendBuf, int chunkSize, int root, int myRank) throws InterruptedException {
        if (myRank == root) {
            if (sendBuf.length != size * chunkSize) {
                throw new IllegalArgumentException("Scatter needs " + (size * chunkSize)
                        + " elements (processes x chunk size) but root has " + sendBuf.length);
            }
            for (int r = 0; r < size; r++) {
                int[] chunk = new int[chunkSize];
                System.arraycopy(sendBuf, r * chunkSize, chunk, 0, chunkSize);
                if (r == root) {
                    continue;           // root's own chunk is handled below
                }
                send(chunk, root, r);
            }
            int[] mine = new int[chunkSize];
            System.arraycopy(sendBuf, root * chunkSize, mine, 0, chunkSize);
            return mine;
        }
        return recv(myRank, root);
    }

    // ---------- MPI_Gather ----------
    /*
     * Gather: the inverse of Scatter. Every rank sends its chunk to the root,
     * and the root places them into ONE buffer in RANK ORDER (rank 0's chunk
     * first, then rank 1's, ...), no matter which rank finished first.
     *   rank0: c0 ; rank1: c1 ; rank2: c2 ;  -->  root: [ c0 | c1 | c2 ]
     *
     * Returns the assembled buffer on the root and null on all other ranks.
     */
    public int[] gather(int[] myChunk, int root, int myRank) throws InterruptedException {
        if (myRank != root) {
            send(myChunk, myRank, root);
            return null;
        }
        int chunkSize = myChunk.length;
        int[] result = new int[size * chunkSize];
        for (int r = 0; r < size; r++) {
            // Receiving from rank r specifically (not "whoever is first") is what
            // guarantees rank order in the result.
            int[] piece = (r == root) ? myChunk : recv(root, r);
            System.arraycopy(piece, 0, result, r * chunkSize, chunkSize);
        }
        return result;
    }

    // Print one line per rank, in rank order, so the demo output is easy to read.
    // (Real MPI output from different ranks is interleaved randomly.)
    public void printInRankOrder(int myRank, String line) throws Exception {
        for (int r = 0; r < size; r++) {
            barrier();
            if (r == myRank) {
                System.out.println(line);
            }
        }
        barrier();
    }
}
