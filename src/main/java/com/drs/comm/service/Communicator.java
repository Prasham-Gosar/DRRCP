package com.drs.comm.service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Communicator
 * ------------
 * Spring service port of legacy MPI communicator simulation (Experiment 9).
 * <p>
 * Simulates MPI_COMM_WORLD where every "rank" is a Java thread and the "network"
 * is a grid of blocking queues:
 * <pre>
 *   mailbox[to][from] = messages that rank 'from' sent to rank 'to'
 * </pre>
 * Primitives implemented:
 * <ul>
 *   <li>MPI_Barrier  -> {@link #barrier()}</li>
 *   <li>MPI_Bcast    -> {@link #bcast(int[], int, int)}</li>
 *   <li>MPI_Scatter  -> {@link #scatter(int[], int, int, int)}</li>
 *   <li>MPI_Gather   -> {@link #gather(int[], int, int)}</li>
 * </ul>
 */
public class Communicator {

    private final int size;
    private final BlockingQueue<int[]>[][] mailbox;
    private final CyclicBarrier barrier;

    @SuppressWarnings("unchecked")
    public Communicator(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Communicator size must be at least 1, got " + size);
        }
        this.size = size;
        this.mailbox = new BlockingQueue[size][size];
        for (int to = 0; to < size; to++) {
            for (int from = 0; from < size; from++) {
                mailbox[to][from] = new LinkedBlockingQueue<>();
            }
        }
        this.barrier = new CyclicBarrier(size);
    }

    /**
     * Total number of ranks in this communicator.
     */
    public int size() {
        return size;
    }

    // ── Point-to-point primitives (MPI_Send / MPI_Recv) ──

    private void send(int[] data, int from, int to) {
        mailbox[to][from].add(data.clone()); // clone simulates real network message serialization
    }

    private int[] recv(int to, int from) throws InterruptedException {
        return mailbox[to][from].take(); // blocks until message arrives in mailbox
    }

    /**
     * MPI_Barrier: Blocks until all ranks in the communicator reach this point.
     */
    public void barrier() throws Exception {
        barrier.await();
    }

    // ── MPI_Bcast ──

    /**
     * Broadcast: The root rank sends the EXACT SAME array to every other rank.
     *
     * @param data   data array on root rank; null on non-root ranks
     * @param root   id of the root rank (0 <= root < size)
     * @param myRank calling rank id
     * @return data array received from root
     */
    public int[] bcast(int[] data, int root, int myRank) throws InterruptedException {
        if (myRank == root) {
            if (data == null) {
                data = new int[0];
            }
            for (int r = 0; r < size; r++) {
                if (r != root) {
                    send(data, root, r);
                }
            }
            return data;
        }
        return recv(myRank, root);
    }

    // ── MPI_Scatter ──

    /**
     * Scatter: The root rank splits its array into 'size' equal chunks of 'chunkSize'
     * and sends chunk i to rank i.
     *
     * @param sendBuf   source buffer on root (length must equal size * chunkSize); null on non-root
     * @param chunkSize number of elements per rank
     * @param root      id of root rank
     * @param myRank    calling rank id
     * @return chunk received by this rank
     */
    public int[] scatter(int[] sendBuf, int chunkSize, int root, int myRank) throws InterruptedException {
        if (myRank == root) {
            if (sendBuf == null || sendBuf.length != size * chunkSize) {
                int len = sendBuf == null ? 0 : sendBuf.length;
                throw new IllegalArgumentException("Scatter needs " + (size * chunkSize)
                        + " elements (processes x chunk size) but root has " + len);
            }
            for (int r = 0; r < size; r++) {
                int[] chunk = new int[chunkSize];
                System.arraycopy(sendBuf, r * chunkSize, chunk, 0, chunkSize);
                if (r == root) {
                    continue; // root's own chunk handled below
                }
                send(chunk, root, r);
            }
            int[] mine = new int[chunkSize];
            System.arraycopy(sendBuf, root * chunkSize, mine, 0, chunkSize);
            return mine;
        }
        return recv(myRank, root);
    }

    // ── MPI_Gather ──

    /**
     * Gather: The inverse of Scatter. Every rank sends its local chunk to root,
     * and root places them into ONE contiguous buffer in strict rank order.
     *
     * @param myChunk local chunk from calling rank
     * @param root    id of root rank
     * @param myRank  calling rank id
     * @return assembled buffer on root; null on non-root ranks
     */
    public int[] gather(int[] myChunk, int root, int myRank) throws InterruptedException {
        if (myRank != root) {
            send(myChunk, myRank, root);
            return null;
        }
        int chunkSize = myChunk.length;
        int[] result = new int[size * chunkSize];
        for (int r = 0; r < size; r++) {
            // Receiving from rank r specifically guarantees rank order in result
            int[] piece = (r == root) ? myChunk : recv(root, r);
            System.arraycopy(piece, 0, result, r * chunkSize, chunkSize);
        }
        return result;
    }
}
