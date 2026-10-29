package com.drs.comm.service;

import com.drs.comm.model.MatrixResult;
import com.drs.comm.model.MatrixSweepResult;
import com.drs.core.event.EventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatrixServiceTest {

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private MatrixService service;

    @Test
    @DisplayName("runMultiplication: computes parallel matrix product and verifies exact match with sequential baseline")
    void testRunMultiplicationCorrectness() {
        int n = 120;
        int threads = 4;

        MatrixResult result = service.runMultiplication(n, threads);

        assertNotNull(result);
        assertEquals(n, result.getN());
        assertEquals(threads, result.getThreads());
        assertTrue(result.isCorrect(), "Parallel matrix multiplication must match sequential baseline 100%");
        assertTrue(result.getSequentialMs() >= 0.0);
        assertTrue(result.getParallelMs() >= 0.0);
        assertTrue(result.getSpeedup() > 0.0);
        assertTrue(result.getEfficiency() > 0.0);

        // Verify top-left sample grid
        assertNotNull(result.getSampleResult());
        assertEquals(4, result.getSampleResult().length);
        assertEquals(4, result.getSampleResult()[0].length);

        // Verify steps logging
        assertFalse(result.getSteps().isEmpty());
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("Correctness Verification: PASSED")));

        // Verify live SSE event published
        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("MATRIX_DONE"), any());
    }

    @Test
    @DisplayName("runMultiplication: handles uneven row division correctly when n % threads != 0")
    void testUnevenRowDivision() {
        int n = 100;
        int threads = 3; // 100 / 3 has remainder 1

        MatrixResult result = service.runMultiplication(n, threads);

        assertNotNull(result);
        assertTrue(result.isCorrect(), "Should handle non-divisible thread counts with remainder rows seamlessly");
    }

    @Test
    @DisplayName("runSweep: runs benchmark points across powers of 2 up to maxThreads")
    void testRunSweep() {
        int n = 80;
        int maxThreads = 4;

        MatrixSweepResult sweepResult = service.runSweep(n, maxThreads);

        assertNotNull(sweepResult);
        assertEquals(n, sweepResult.getN());
        assertEquals(maxThreads, sweepResult.getMaxThreads());
        assertFalse(sweepResult.getRuns().isEmpty());

        for (MatrixResult run : sweepResult.getRuns()) {
            assertTrue(run.isCorrect());
            assertTrue(run.getThreads() <= maxThreads);
            assertTrue(run.getSpeedup() > 0.0);
        }

        assertTrue(sweepResult.getBestThreads() >= 1);
        assertTrue(sweepResult.getBestSpeedup() > 0.0);

        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("MATRIX_DONE"), any());
    }

    @Test
    @DisplayName("Validation: rejects dimension n out of bounds [2..800] or invalid thread counts")
    void testValidationLimits() {
        // n > 800
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> service.validateInputs(801, 4));
        assertTrue(ex1.getMessage().contains("between 2 and 800"));

        // n < 2
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> service.validateInputs(1, 4));
        assertTrue(ex2.getMessage().contains("between 2 and 800"));

        // threads < 1
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () -> service.validateInputs(100, 0));
        assertTrue(ex3.getMessage().contains("Thread count must be between 1 and 32"));

        // threads > 32
        IllegalArgumentException ex4 = assertThrows(IllegalArgumentException.class, () -> service.validateInputs(100, 33));
        assertTrue(ex4.getMessage().contains("Thread count must be between 1 and 32"));
    }
}
