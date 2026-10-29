package com.drs.compute.service;

import com.drs.compute.model.AnalyticsResult;
import com.drs.compute.model.AnalyticsRun;
import com.drs.compute.model.RescueTaskEntity;
import com.drs.compute.repository.AnalyticsRunRepository;
import com.drs.compute.repository.RescueTaskRepository;
import com.drs.core.event.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Exp 7: SparkJobService and Fallback Coordination")
class SparkJobServiceTest {

    @Mock
    private RescueTaskRepository taskRepository;

    @Mock
    private AnalyticsRunRepository runRepository;

    @Mock
    private EventPublisher eventPublisher;

    private MapReduceFallbackService fallbackService;
    private ObjectMapper mapper;
    private SparkJobService sparkJobService;

    @BeforeEach
    void setUp() {
        fallbackService = new MapReduceFallbackService();
        mapper = new ObjectMapper();
        sparkJobService = new SparkJobService(taskRepository, runRepository, fallbackService, eventPublisher, mapper);
        ReflectionTestUtils.setField(sparkJobService, "defaultEngine", "streams");
        ReflectionTestUtils.setField(sparkJobService, "configuredJarPath", "non-existent-spark.jar");
    }

    @Test
    @DisplayName("Executes streams engine, publishes events, and persists AnalyticsRun entity")
    void testRunAnalyticsStreamsMode() {
        List<RescueTaskEntity> tasks = List.of(
                new RescueTaskEntity("T1", "Search survivors", 4.0, "Cluster-A", "Search", "PENDING", null),
                new RescueTaskEntity("T2", "Deliver medicine", 2.0, "Cluster-A", "Medical", "PENDING", null)
        );
        when(taskRepository.findAll()).thenReturn(tasks);

        AnalyticsResult result = sparkJobService.runAnalytics("streams");

        assertNotNull(result);
        assertEquals(2, result.getTotalTasks());
        assertEquals(6.0, result.getTotalWorkload());
        assertEquals("streams", result.getEngine());
        assertFalse(result.isFallbackUsed());

        // Verify EventPublisher calls
        verify(eventPublisher, times(1)).publish(eq("compute"), eq("ANALYTICS_STARTED"), any());
        verify(eventPublisher, times(1)).publish(eq("compute"), eq("ANALYTICS_DONE"), eq(result));

        // Verify persistence
        ArgumentCaptor<AnalyticsRun> runCaptor = ArgumentCaptor.forClass(AnalyticsRun.class);
        verify(runRepository, times(1)).save(runCaptor.capture());
        AnalyticsRun savedRun = runCaptor.getValue();
        assertEquals("streams", savedRun.getEngine());
        assertEquals(2, savedRun.getTotalTasks());
        assertEquals(6.0, savedRun.getTotalWorkload());
        assertEquals("SUCCESS", savedRun.getStatus());
        assertNotNull(savedRun.getResultJson());
    }

    @Test
    @DisplayName("Seamlessly falls back to MapReduceFallbackService when Spark jar is not found")
    void testSparkFallbackWhenJarMissing() {
        List<RescueTaskEntity> tasks = List.of(
                new RescueTaskEntity("T1", "Water delivery", 5.0, "Cluster-B", "Food", "PENDING", null)
        );
        when(taskRepository.findAll()).thenReturn(tasks);

        AnalyticsResult result = sparkJobService.runAnalytics("spark");

        assertNotNull(result);
        assertEquals("streams-fallback", result.getEngine());
        assertTrue(result.isFallbackUsed());
        assertEquals(1, result.getTotalTasks());
        assertEquals(5.0, result.getTotalWorkload());

        // Verify step trace recorded the fallback note
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("[WARN]")));
        verify(runRepository, times(1)).save(any(AnalyticsRun.class));
    }

    @Test
    @DisplayName("Seeds default tasks when database repository is empty")
    void testAutoSeedingWhenEmpty() {
        when(taskRepository.findAll()).thenReturn(new ArrayList<>());
        when(taskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AnalyticsResult result = sparkJobService.runAnalytics("streams");

        assertNotNull(result);
        assertEquals(10, result.getTotalTasks());
        assertEquals(41.0, result.getTotalWorkload());
        verify(taskRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("Retrieves latest analytics from persisted repository")
    void testGetLatestAnalytics() throws Exception {
        AnalyticsResult dummy = new AnalyticsResult(
                "DRS-SparkAnalytics", "2026-10-29T10:00:00Z", "input",
                5, 20.0, null, null, null, null, null, "streams", 12, false, null
        );
        String json = mapper.writeValueAsString(dummy);
        AnalyticsRun run = new AnalyticsRun("streams", 12, 5, 20.0, "SUCCESS", json);
        when(runRepository.findTopByOrderByTimestampDesc()).thenReturn(Optional.of(run));

        Optional<AnalyticsResult> latest = sparkJobService.getLatestAnalytics();

        assertTrue(latest.isPresent());
        assertEquals(5, latest.get().getTotalTasks());
        assertEquals(20.0, latest.get().getTotalWorkload());
    }

    @Test
    @DisplayName("Rejects concurrent analytics execution when a run is already active")
    void testConcurrentRunRejected() {
        AtomicBoolean guard = (AtomicBoolean) ReflectionTestUtils.getField(sparkJobService, "analyticsRunning");
        assertNotNull(guard);
        guard.set(true);

        try {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    sparkJobService.runAnalytics("streams")
            );
            assertTrue(ex.getMessage().contains("already running"));
        } finally {
            guard.set(false);
        }
    }
}
