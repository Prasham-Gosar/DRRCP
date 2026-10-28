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
 *   <li>MPI_Bcast    -> {@link #bcast(int[], int, int)} and {@link #bcastText(String, int, int)}</li>
 *   <li>MPI_Scatter  -> {@link #scatter(int[], int, int, int)} and {@link #scatterStrings(String[], int, int, int)}</li>
 *   <li>MPI_Gather   -> {@link #gather(int[], int, int)} and {@link #gatherStrings(String[], int, int)}</li>
 * </ul>
 */
public class Communicator {

    private final int size;
    private final BlockingQueue<int[]>[][] mailbox;
    private final BlockingQueue<String>[][] textMailbox;
    private final BlockingQueue<String[]>[][] stringMailbox;
    private final CyclicBarrier barrier;

    @SuppressWarnings("unchecked")
    public Communicator(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Communicator size must be at least 1, got " + size);
        }
        this.size = size;
        this.mailbox = new BlockingQueue[size][size];
        this.textMailbox = new BlockingQueue[size][size];
        this.stringMailbox = new BlockingQueue[size][size];

        for (int to = 0; to < size; to++) {
            for (int from = 0; from < size; from++) {
                mailbox[to][from] = new LinkedBlockingQueue<>();
                textMailbox[to][from] = new LinkedBlockingQueue<>();
                stringMailbox[to][from] = new LinkedBlockingQueue<>();
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

    // ── MPI_Bcast (int[] & String) ──

    /**
     * Broadcast: The root rank sends the EXACT SAME array to every other rank.
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

    /**
     * Broadcasts an emergency alert text string from root rank to all ranks.
     */
    public String bcastText(String text, int root, int myRank) throws InterruptedException {
        if (myRank == root) {
            String val = (text != null) ? text : "";
            for (int r = 0; r < size; r++) {
                if (r != root) {
                    textMailbox[r][root].add(val);
                }
            }
            return val;
        }
        return textMailbox[myRank][root].take();
    }

    // ── MPI_Scatter (int[] & String[]) ──

    /**
     * Scatter: The root rank splits its array into 'size' equal chunks of 'chunkSize'
     * and sends chunk i to rank i.
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

    /**
     * Scatter: Splits a String[] array (e.g. disaster zone IDs) into equal chunks per rank.
     */
    public String[] scatterStrings(String[] sendBuf, int chunkSize, int root, int myRank) throws InterruptedException {
        if (myRank == root) {
            if (sendBuf == null || sendBuf.length != size * chunkSize) {
                int len = sendBuf == null ? 0 : sendBuf.length;
                throw new IllegalArgumentException("Scatter needs " + (size * chunkSize)
                        + " elements but root has " + len);
            }
            for (int r = 0; r < size; r++) {
                String[] chunk = new String[chunkSize];
                System.arraycopy(sendBuf, r * chunkSize, chunk, 0, chunkSize);
                if (r == root) {
                    continue;
                }
                stringMailbox[r][root].add(chunk.clone());
            }
            String[] mine = new String[chunkSize];
            System.arraycopy(sendBuf, root * chunkSize, mine, 0, chunkSize);
            return mine;
        }
        return stringMailbox[myRank][root].take();
    }

    // ── MPI_Gather (int[] & String[]) ──

    /**
     * Gather: Every rank sends its local chunk to root, and root places them
     * into ONE contiguous buffer in strict rank order.
     */
    public int[] gather(int[] myChunk, int root, int myRank) throws InterruptedException {
        if (myRank != root) {
            send(myChunk, myRank, root);
            return null;
        }
        int chunkSize = myChunk.length;
        int[] result = new int[size * chunkSize];
        for (int r = 0; r < size; r++) {
            int[] piece = (r == root) ? myChunk : recv(root, r);
            System.arraycopy(piece, 0, result, r * chunkSize, chunkSize);
        }
        return result;
    }

    /**
     * Gather: Collects local delivery reports (String[]) from all ranks into root in strict rank order.
     */
    public String[] gatherStrings(String[] myChunk, int root, int myRank) throws InterruptedException {
        if (myRank != root) {
            stringMailbox[root][myRank].add(myChunk.clone());
            return null;
        }
        int chunkSize = myChunk.length;
        String[] result = new String[size * chunkSize];
        for (int r = 0; r < size; r++) {
            String[] piece = (r == root) ? myChunk : stringMailbox[root][r].take();
            System.arraycopy(piece, 0, result, r * chunkSize, chunkSize);
        }
        return result;
    }
}
