package com.drs.comm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Communicator}.
 */
class CommunicatorTest {

    @Test
    @DisplayName("Communicator size matches initialized process count")
    void communicatorSize() {
        Communicator comm = new Communicator(4);
        assertEquals(4, comm.size());
    }

    @Test
    @DisplayName("Communicator throws exception for invalid size")
    void invalidCommunicatorSize() {
        assertThrows(IllegalArgumentException.class, () -> new Communicator(0));
    }

    @Test
    @DisplayName("MPI_Bcast sends identical data from root to all ranks")
    void broadcastAcrossRanks() throws Exception {
        int size = 3;
        int root = 0;
        Communicator comm = new Communicator(size);
        int[] sourceData = new int[]{911, 42, 7};
        int[][] received = new int[size][];

        Thread[] threads = new Thread[size];
        for (int r = 0; r < size; r++) {
            final int rank = r;
            threads[r] = new Thread(() -> {
                try {
                    int[] myData = (rank == root) ? sourceData : null;
                    received[rank] = comm.bcast(myData, root, rank);
                } catch (Exception e) {
                    fail("Rank " + rank + " failed: " + e.getMessage());
                }
            });
            threads[r].start();
        }

        for (Thread t : threads) {
            t.join(3000);
        }

        for (int r = 0; r < size; r++) {
            assertNotNull(received[r], "Rank " + r + " should receive data");
            assertArrayEquals(sourceData, received[r], "Rank " + r + " data must match source");
        }
    }

    @Test
    @DisplayName("MPI_Scatter divides source buffer into equal chunks across ranks")
    void scatterAcrossRanks() throws Exception {
        int size = 4;
        int chunkSize = 2;
        int root = 0;
        Communicator comm = new Communicator(size);
        int[] sendBuf = new int[]{10, 20, 30, 40, 50, 60, 70, 80};
        int[][] received = new int[size][];

        Thread[] threads = new Thread[size];
        for (int r = 0; r < size; r++) {
            final int rank = r;
            threads[r] = new Thread(() -> {
                try {
                    int[] buf = (rank == root) ? sendBuf : null;
                    received[rank] = comm.scatter(buf, chunkSize, root, rank);
                } catch (Exception e) {
                    fail("Rank " + rank + " failed: " + e.getMessage());
                }
            });
            threads[r].start();
        }

        for (Thread t : threads) {
            t.join(3000);
        }

        assertArrayEquals(new int[]{10, 20}, received[0]);
        assertArrayEquals(new int[]{30, 40}, received[1]);
        assertArrayEquals(new int[]{50, 60}, received[2]);
        assertArrayEquals(new int[]{70, 80}, received[3]);
    }

    @Test
    @DisplayName("MPI_Scatter throws IllegalArgumentException if buffer length does not match size * chunkSize")
    void scatterInvalidBufferLength() {
        Communicator comm = new Communicator(4);
        int[] invalidBuffer = new int[]{1, 2, 3}; // needs 8
        assertThrows(IllegalArgumentException.class, () -> comm.scatter(invalidBuffer, 2, 0, 0));
    }

    @Test
    @DisplayName("MPI_Gather collects local chunks in strict rank order on root")
    void gatherAcrossRanks() throws Exception {
        int size = 3;
        int chunkSize = 2;
        int root = 0;
        Communicator comm = new Communicator(size);
        int[][] gatheredRef = new int[size][];

        Thread[] threads = new Thread[size];
        for (int r = 0; r < size; r++) {
            final int rank = r;
            threads[r] = new Thread(() -> {
                try {
                    int[] localData = new int[]{(rank + 1) * 10, (rank + 1) * 10 + 1};
                    gatheredRef[rank] = comm.gather(localData, root, rank);
                } catch (Exception e) {
                    fail("Rank " + rank + " failed: " + e.getMessage());
                }
            });
            threads[r].start();
        }

        for (Thread t : threads) {
            t.join(3000);
        }

        // Non-roots should return null
        assertNull(gatheredRef[1]);
        assertNull(gatheredRef[2]);

        // Root should assemble all in rank order
        assertNotNull(gatheredRef[0]);
        assertArrayEquals(new int[]{10, 11, 20, 21, 30, 31}, gatheredRef[0]);
    }

    @Test
    @DisplayName("MPI_Barrier synchronizes all ranks")
    void barrierSynchronizesAll() throws Exception {
        int size = 4;
        Communicator comm = new Communicator(size);
        AtomicInteger counter = new AtomicInteger(0);

        Thread[] threads = new Thread[size];
        for (int r = 0; r < size; r++) {
            threads[r] = new Thread(() -> {
                try {
                    counter.incrementAndGet();
                    comm.barrier();
                    // After barrier, all 4 must have incremented
                    assertEquals(size, counter.get());
                } catch (Exception e) {
                    fail("Barrier failed: " + e.getMessage());
                }
            });
            threads[r].start();
        }

        for (Thread t : threads) {
            t.join(3000);
        }

        assertEquals(size, counter.get());
    }
}
