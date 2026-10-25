import mpi.MPI;
import mpi.MPIException;

public class ParallelMatrixMultiplication {

    private static final int DEFAULT_N = 400;
    private static final int ROOT = 0;

    // Sequential matrix multiplication
    private static void sequentialMultiply(
            int[] A, int[] B, int[] C, int n) {

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

    // Display a small portion of the result matrix
    private static void printMatrixSample(int[] C, int n) {
        int limit = Math.min(n, 4);

        System.out.println(
                "Top-left " + limit + " x " + limit
                        + " result sample:");

        for (int i = 0; i < limit; i++) {
            for (int j = 0; j < limit; j++) {
                System.out.printf("%8d ", C[i * n + j]);
            }
            System.out.println();
        }
    }

    public static void main(String[] args)
            throws MPIException {

        String sizeProperty =
                System.getProperty("matrixSize", "");

        int requestedN = DEFAULT_N;

        if (!sizeProperty.isEmpty()) {
            try {
                requestedN = Integer.parseInt(sizeProperty);
            } catch (NumberFormatException e) {
                System.err.println(
                        "Invalid matrixSize property: "
                                + sizeProperty);
                System.exit(1);
                return;
            }
        }

        // STEP 1: Initialize MPI
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        // Broadcast matrix size to all processes.
        int[] nBuffer = { requestedN };

        MPI.COMM_WORLD.Bcast(
                nBuffer, 0, 1, MPI.INT, ROOT);

        int n = nBuffer[0];

        if (rank == ROOT) {
            System.out.println("Matrix size received: " + n);
            System.out.println("MPI processes: " + size);
        }

        // Validate matrix size.
        if (n <= 0 || n % size != 0) {
            if (rank == ROOT) {
                System.out.println(
                        "Error: matrix size must be positive "
                                + "and divisible by process count.");
                System.out.println(
                        "Use a positive multiple of " + size + ".");
            }

            MPI.Finalize();
            return;
        }

        int rowsPerProcess = n / size;
        int elementsPerProcess = rowsPerProcess * n;

        int[] A = null;
        int[] B = new int[n * n];
        int[] C = null;
        int[] sequentialC = null;

        int[] localA = new int[elementsPerProcess];
        int[] localC = new int[elementsPerProcess];

        double sequentialTime = 0.0;

        // STEP 2: Root initializes the matrices.
        if (rank == ROOT) {
            A = new int[n * n];
            C = new int[n * n];
            sequentialC = new int[n * n];

            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    A[i * n + j] = (i + j) % 10 + 1;
                    B[i * n + j] = (2 * i + j) % 10 + 1;
                }
            }

            System.out.println();
            System.out.println(
                    "PARALLEL MATRIX MULTIPLICATION");
            System.out.println(
                    "Matrix size        : " + n + " x " + n);
            System.out.println(
                    "Number of processes: " + size);
            System.out.println(
                    "Rows per process   : " + rowsPerProcess);

            // Calculate sequential result and measure its time.
            double start = MPI.Wtime();

            sequentialMultiply(A, B, sequentialC, n);

            sequentialTime = MPI.Wtime() - start;

            System.out.printf(
                    "Sequential time    : %.6f seconds%n",
                    sequentialTime);
        }

        // Synchronize before starting parallel timing.
        MPI.COMM_WORLD.Barrier();

        double parallelStart = MPI.Wtime();

        // STEP 3: Broadcast matrix B to all processes.
        MPI.COMM_WORLD.Bcast(
                B, 0, n * n, MPI.INT, ROOT);

        // STEP 4: Scatter rows of matrix A.
        MPI.COMM_WORLD.Scatter(
                A, 0, elementsPerProcess, MPI.INT,
                localA, 0, elementsPerProcess, MPI.INT,
                ROOT);

        System.out.println(
                "[Process " + rank + "] Received "
                        + rowsPerProcess + " rows.");

        // STEP 5: Each process computes its local result.
        for (int i = 0; i < rowsPerProcess; i++) {
            for (int j = 0; j < n; j++) {
                int sum = 0;

                for (int k = 0; k < n; k++) {
                    sum += localA[i * n + k]
                            * B[k * n + j];
                }

                localC[i * n + j] = sum;
            }
        }

        // STEP 6: Gather partial results at root.
        MPI.COMM_WORLD.Gather(
                localC, 0, elementsPerProcess, MPI.INT,
                C, 0, elementsPerProcess, MPI.INT,
                ROOT);

        // Ensure all processes have completed before timing ends.
        MPI.COMM_WORLD.Barrier();

        double parallelTime = MPI.Wtime() - parallelStart;

        // STEP 7: Verify result and display timings.
        if (rank == ROOT) {
            boolean correct = true;

            for (int i = 0; i < n * n; i++) {
                if (C[i] != sequentialC[i]) {
                    correct = false;
                    break;
                }
            }

            System.out.println();
            System.out.println(
                    "PARALLEL COMPUTATION RESULTS");

            System.out.println(
                    "Sequential result  : calculated");

            System.out.println(
                    "Result verification: "
                            + (correct ? "PASSED" : "FAILED"));

            System.out.printf(
                    "Parallel time      : %.6f seconds%n",
                    parallelTime);

            if (parallelTime > 0.0) {
                System.out.printf(
                        "Speedup            : %.4f%n",
                        sequentialTime / parallelTime);
            }

            printMatrixSample(C, n);
        }

        // STEP 8: Finalize MPI.
        MPI.Finalize();
    }
}