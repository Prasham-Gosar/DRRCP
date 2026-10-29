package com.drs.comm.service;

import com.drs.comm.model.MatrixResult;
import com.drs.comm.model.MatrixSweepResult;
import com.drs.core.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * MatrixService
 * -------------
 * Spring service port of legacy Experiment 10: Parallel Matrix Multiplication.
 * <p>
 * Simulates high-performance computing in a disaster relief zone:
 * Multiplies a Zone Risk Assessment matrix (A) by an Emergency Resource Demand matrix (B)
 * to calculate real-time Priority Score tensors across distributed disaster sectors.
 * <p>
 * Demonstrates:
 * 1. Sequential computation baseline.
 * 2. Parallel thread-based scatter/gather row decomposition.
 * 3. Exact element-by-element verification against sequential output.
 * 4. Speedup (T_seq / T_par) and Efficiency (Speedup / Threads) benchmarks.
 * 5. Multi-threaded scalability sweeps (1, 2, 4, 8... threads).
 */
@Service
public class MatrixService {

    private static final Logger log = LoggerFactory.getLogger(MatrixService.class);
    public static final int MAX_N = 800;
    public static final int MIN_N = 2;
    public static final int MAX_THREADS = 32;

    private final EventPublisher eventPublisher;

    public MatrixService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Executes a single benchmark comparing sequential multiplication vs parallel multiplication
     * across the requested number of worker threads.
     *
     * @param n       Matrix dimension (n x n)
     * @param threads Worker thread count
     * @return Benchmarked MatrixResult with timing, speedup, verification, and sample output
     */
    public MatrixResult runMultiplication(int n, int threads) {
        validateInputs(n, threads);

        MatrixResult result = new MatrixResult(n, threads);
        result.setCompletedAt(Instant.now().toString());

        result.addStep(String.format("Initializing %d x %d Zone-Risk (A) and Resource-Demand (B) matrices...", n, n));

        // 1. Initialize matrices A and B using the legacy formula
        int[] A = new int[n * n];
        int[] B = new int[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                A[i * n + j] = (i + j) % 10 + 1;
                B[i * n + j] = (2 * i + j) % 10 + 1;
            }
        }

        // 2. Sequential multiplication baseline
        int[] sequentialC = new int[n * n];
        long seqStartNano = System.nanoTime();
        multiplySequential(A, B, sequentialC, n);
        long seqEndNano = System.nanoTime();
        double seqMs = roundToTwoDecimals((seqEndNano - seqStartNano) / 1_000_000.0);
        result.setSequentialMs(seqMs);
        result.addStep(String.format("Sequential execution completed in %.2f ms", seqMs));

        // 3. Parallel multiplication with row-wise scatter/gather
        int[] parallelC = new int[n * n];
        long parStartNano = System.nanoTime();
        multiplyParallel(A, B, parallelC, n, threads);
        long parEndNano = System.nanoTime();
        double parMs = roundToTwoDecimals((parEndNano - parStartNano) / 1_000_000.0);
        result.setParallelMs(parMs);
        result.addStep(String.format("Parallel execution (%d threads) completed in %.2f ms", threads, parMs));

        // 4. Result Verification (Parallel == Sequential)
        boolean correct = true;
        for (int i = 0; i < n * n; i++) {
            if (parallelC[i] != sequentialC[i]) {
                correct = false;
                break;
            }
        }
        result.setCorrect(correct);
        result.addStep("Correctness Verification: " + (correct ? "PASSED (100% Match)" : "FAILED (Mismatch Detected)"));

        // 5. Calculate Speedup and Efficiency
        double speedup = (parMs > 0.0) ? roundToTwoDecimals(seqMs / parMs) : 1.0;
        double efficiency = roundToTwoDecimals(speedup / threads);
        result.setSpeedup(speedup);
        result.setEfficiency(efficiency);
        result.addStep(String.format("Speedup: %.2fx | Parallel Efficiency: %.2f (%.1f%%)", speedup, efficiency, efficiency * 100));

        // 6. Extract top-left sample matrix (up to 4x4)
        result.setSampleResult(extractSample(parallelC, n, 4));

        // 7. Publish live SSE event
        publishMatrixEvent(result);

        return result;
    }

    /**
     * Executes a benchmark sweep across thread counts = 1, 2, 4, 8... up to maxThreads.
     *
     * @param n          Matrix dimension
     * @param maxThreads Upper thread count boundary
     * @return MatrixSweepResult containing benchmark points for charting
     */
    public MatrixSweepResult runSweep(int n, int maxThreads) {
        validateInputs(n, 1);
        int effectiveMaxThreads = Math.min(Math.max(1, maxThreads), MAX_THREADS);

        MatrixSweepResult sweepResult = new MatrixSweepResult(n, effectiveMaxThreads);
        sweepResult.setCompletedAt(Instant.now().toString());

        // Precompute baseline matrices once for consistency across sweep points
        int[] A = new int[n * n];
        int[] B = new int[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                A[i * n + j] = (i + j) % 10 + 1;
                B[i * n + j] = (2 * i + j) % 10 + 1;
            }
        }

        // Sequential baseline
        int[] sequentialC = new int[n * n];
        long seqStartNano = System.nanoTime();
        multiplySequential(A, B, sequentialC, n);
        long seqEndNano = System.nanoTime();
        double seqMs = roundToTwoDecimals((seqEndNano - seqStartNano) / 1_000_000.0);

        int bestThreads = 1;
        double bestSpeedup = 1.0;

        // Generate powers-of-two thread list: 1, 2, 4, 8, 16, 32 (including maxThreads if not a power of 2)
        Set<Integer> threadCounts = new TreeSet<>();
        for (int t = 1; t <= effectiveMaxThreads; t *= 2) {
            threadCounts.add(t);
        }
        threadCounts.add(effectiveMaxThreads);

        for (int t : threadCounts) {
            int[] parallelC = new int[n * n];
            long parStartNano = System.nanoTime();
            multiplyParallel(A, B, parallelC, n, t);
            long parEndNano = System.nanoTime();
            double parMs = roundToTwoDecimals((parEndNano - parStartNano) / 1_000_000.0);

            boolean correct = true;
            for (int i = 0; i < n * n; i++) {
                if (parallelC[i] != sequentialC[i]) {
                    correct = false;
                    break;
                }
            }

            double speedup = (parMs > 0.0) ? roundToTwoDecimals(seqMs / parMs) : 1.0;
            double efficiency = roundToTwoDecimals(speedup / t);

            MatrixResult runResult = new MatrixResult(n, t);
            runResult.setSequentialMs(seqMs);
            runResult.setParallelMs(parMs);
            runResult.setSpeedup(speedup);
            runResult.setEfficiency(efficiency);
            runResult.setCorrect(correct);
            runResult.setCompletedAt(Instant.now().toString());

            if (speedup > bestSpeedup) {
                bestSpeedup = speedup;
                bestThreads = t;
            }

            sweepResult.addRun(runResult);
        }

        sweepResult.setBestThreads(bestThreads);
        sweepResult.setBestSpeedup(bestSpeedup);

        // Publish live SSE event for sweep completion
        eventPublisher.publish("comm", "MATRIX_DONE", Map.of(
                "operation", "MATRIX_SWEEP",
                "n", n,
                "maxThreads", effectiveMaxThreads,
                "bestThreads", bestThreads,
                "bestSpeedup", bestSpeedup,
                "runsCount", sweepResult.getRuns().size()
        ));

        return sweepResult;
    }

    /**
     * Sequential matrix multiplication: C = A x B.
     */
    public void multiplySequential(int[] A, int[] B, int[] C, int n) {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int sum = 0;
                for (int k = 0; k < n; k++) {
                    sum += A[i * n + k] * B[k * n + j];
                }
                C[i * n + j] = sum;
            }
        }
    }

    /**
     * Parallel matrix multiplication:
     * Decomposes the rows of matrix A across worker threads (MPI scatter/gather row pattern).
     */
    public void multiplyParallel(int[] A, int[] B, int[] C, int n, int threads) {
        int actualThreads = Math.min(threads, n);
        Thread[] workerThreads = new Thread[actualThreads];

        int baseRows = n / actualThreads;
        int remainder = n % actualThreads;

        for (int t = 0; t < actualThreads; t++) {
            final int startRow = t * baseRows + Math.min(t, remainder);
            final int endRow = startRow + baseRows + (t < remainder ? 1 : 0);

            workerThreads[t] = new Thread(() -> {
                for (int i = startRow; i < endRow; i++) {
                    for (int j = 0; j < n; j++) {
                        int sum = 0;
                        for (int k = 0; k < n; k++) {
                            sum += A[i * n + k] * B[k * n + j];
                        }
                        C[i * n + j] = sum;
                    }
                }
            }, "MatrixWorker-" + t);
            workerThreads[t].start();
        }

        for (Thread worker : workerThreads) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Matrix worker thread interrupted: {}", e.getMessage());
            }
        }
    }

    /**
     * Extracts a top-left sample grid of size limit x limit from flat array C.
     */
    public int[][] extractSample(int[] C, int n, int limit) {
        int actualLimit = Math.min(n, limit);
        int[][] sample = new int[actualLimit][actualLimit];
        for (int i = 0; i < actualLimit; i++) {
            for (int j = 0; j < actualLimit; j++) {
                sample[i][j] = C[i * n + j];
            }
        }
        return sample;
    }

    public void validateInputs(int n, int threads) {
        if (n < MIN_N || n > MAX_N) {
            throw new IllegalArgumentException(String.format("Matrix dimension n must be between %d and %d (received: %d)", MIN_N, MAX_N, n));
        }
        if (threads < 1 || threads > MAX_THREADS) {
            throw new IllegalArgumentException(String.format("Thread count must be between 1 and %d (received: %d)", MAX_THREADS, threads));
        }
    }

    private void publishMatrixEvent(MatrixResult res) {
        eventPublisher.publish("comm", "MATRIX_DONE", Map.of(
                "operation", "MATRIX_RUN",
                "n", res.getN(),
                "threads", res.getThreads(),
                "sequentialMs", res.getSequentialMs(),
                "parallelMs", res.getParallelMs(),
                "speedup", res.getSpeedup(),
                "efficiency", res.getEfficiency(),
                "correct", res.isCorrect()
        ));
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
