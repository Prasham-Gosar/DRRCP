package com.drs.comm.controller;

import com.drs.comm.model.MatrixRequest;
import com.drs.comm.model.MatrixResult;
import com.drs.comm.model.MatrixSweepResult;
import com.drs.comm.service.MatrixService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for Parallel Matrix Multiplication (Experiment 10).
 * Provides endpoints for isolated benchmark runs and multi-threaded scalability sweeps.
 */
@RestController
@RequestMapping("/api/matrix")
public class MatrixController {

    private final MatrixService matrixService;

    public MatrixController(MatrixService matrixService) {
        this.matrixService = matrixService;
    }

    /**
     * Executes sequential vs parallel matrix multiplication for given n and thread count.
     */
    @PostMapping("/run")
    public ResponseEntity<MatrixResult> runBenchmark(@RequestBody(required = false) MatrixRequest request) {
        MatrixRequest req = (request != null) ? request : new MatrixRequest();
        MatrixResult result = matrixService.runMultiplication(req.getN(), req.getThreads());
        return ResponseEntity.ok(result);
    }

    /**
     * Executes multi-threaded benchmark sweep across threads = 1, 2, 4, 8... up to maxThreads.
     */
    @PostMapping("/sweep")
    public ResponseEntity<MatrixSweepResult> runSweep(@RequestBody(required = false) MatrixRequest request) {
        MatrixRequest req = (request != null) ? request : new MatrixRequest();
        MatrixSweepResult sweepResult = matrixService.runSweep(req.getN(), req.getMaxThreads());
        return ResponseEntity.ok(sweepResult);
    }

    /**
     * Returns Experiment 10 capability metadata and configuration limits.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "experiment", 10,
                "name", "Parallel Matrix Multiplication (MPI Row Scatter/Gather)",
                "defaultN", 400,
                "maxN", MatrixService.MAX_N,
                "minN", MatrixService.MIN_N,
                "maxThreads", MatrixService.MAX_THREADS,
                "disasterContext", "Zone-Risk Assessment Tensor x Emergency Resource Demand Matrix"
        ));
    }

    /**
     * Handles invalid inputs (e.g., n > 800, n < 2, threads < 1).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", "ERROR",
                "errorMessage", ex.getMessage(),
                "correct", false
        ));
    }
}
