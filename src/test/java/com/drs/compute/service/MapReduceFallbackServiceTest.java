package com.drs.compute.service;

import com.drs.compute.model.AnalyticsResult;
import com.drs.compute.model.RescueTaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Exp 7: MapReduce Fallback Engine (Java Streams)")
class MapReduceFallbackServiceTest {

    private MapReduceFallbackService fallbackService;

    @BeforeEach
    void setUp() {
        fallbackService = new MapReduceFallbackService();
    }

    @Test
    @DisplayName("Successfully computes MapReduce aggregates matching legacy rescue task dataset")
    void testLegacyDatasetMapReduce() {
        List<RescueTaskEntity> tasks = List.of(
                new RescueTaskEntity("T1", "Task 1", 5.0, "ClusterA", "Medical", "PENDING", null),
                new RescueTaskEntity("T2", "Task 2", 3.0, "ClusterA", "Food", "PENDING", null),
                new RescueTaskEntity("T3", "Task 3", 4.0, "ClusterB", "Medical", "PENDING", null),
                new RescueTaskEntity("T4", "Task 4", 6.0, "ClusterB", "Search", "PENDING", null),
                new RescueTaskEntity("T5", "Task 5", 2.0, "ClusterA", "Medical", "PENDING", null),
                new RescueTaskEntity("T6", "Task 6", 5.0, "ClusterB", "Food", "PENDING", null),
                new RescueTaskEntity("T7", "Task 7", 7.0, "ClusterC", "Search", "PENDING", null),
                new RescueTaskEntity("T8", "Task 8", 3.0, "ClusterC", "Medical", "PENDING", null),
                new RescueTaskEntity("T9", "Task 9", 4.0, "ClusterA", "Search", "PENDING", null),
                new RescueTaskEntity("T10", "Task 10", 2.0, "ClusterC", "Food", "PENDING", null)
        );

        AnalyticsResult result = fallbackService.computeAnalytics(tasks, "test-input", 15, false);

        assertNotNull(result);
        assertEquals(10, result.getTotalTasks());
        assertEquals(41.0, result.getTotalWorkload(), 0.001);
        assertEquals("streams", result.getEngine());
        assertFalse(result.isFallbackUsed());

        // Verify Cluster marginals
        assertEquals(4, result.getCountsPerCluster().get("ClusterA"));
        assertEquals(14.0, result.getWorkloadPerCluster().get("ClusterA"), 0.001);

        assertEquals(3, result.getCountsPerCluster().get("ClusterB"));
        assertEquals(15.0, result.getWorkloadPerCluster().get("ClusterB"), 0.001);

        assertEquals(3, result.getCountsPerCluster().get("ClusterC"));
        assertEquals(12.0, result.getWorkloadPerCluster().get("ClusterC"), 0.001);

        // Verify Task Type marginals
        assertEquals(4, result.getCountsPerType().get("Medical"));
        assertEquals(14.0, result.getWorkloadPerType().get("Medical"), 0.001);

        assertEquals(3, result.getCountsPerType().get("Food"));
        assertEquals(10.0, result.getWorkloadPerType().get("Food"), 0.001);

        assertEquals(3, result.getCountsPerType().get("Search"));
        assertEquals(17.0, result.getWorkloadPerType().get("Search"), 0.001);

        // Verify fine-grained breakdown
        assertFalse(result.getBreakdown().isEmpty());
        assertTrue(result.getBreakdown().stream().anyMatch(b ->
                "ClusterA".equals(b.getCluster()) && "Medical".equals(b.getTaskType()) && b.getTaskCount() == 2 && b.getTotalWorkload() == 7.0
        ));

        // Verify execution step trace is present
        assertFalse(result.getSteps().isEmpty());
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("[MAP]")));
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("[SHUFFLE/SORT]")));
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("[REDUCE]")));
    }

    @Test
    @DisplayName("Gracefully handles empty task list")
    void testEmptyTasks() {
        AnalyticsResult result = fallbackService.computeAnalytics(List.of(), "empty", 0, false);

        assertNotNull(result);
        assertEquals(0, result.getTotalTasks());
        assertEquals(0.0, result.getTotalWorkload());
        assertTrue(result.getCountsPerCluster().isEmpty());
        assertTrue(result.getBreakdown().isEmpty());
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("[WARN]")));
    }

    @Test
    @DisplayName("Correctly tags engine as streams-fallback when isFallback is true")
    void testFallbackTagging() {
        List<RescueTaskEntity> tasks = List.of(
                new RescueTaskEntity("T1", "Task 1", 3.0, "ClusterA", "Search", "PENDING", null)
        );

        AnalyticsResult result = fallbackService.computeAnalytics(tasks, "fallback-input", 45, true);

        assertEquals("streams-fallback", result.getEngine());
        assertTrue(result.isFallbackUsed());
        assertEquals(45, result.getDurationMs());
    }

    @Test
    @DisplayName("Single task produces exactly one composite bucket without unnecessary splits")
    void testSingleTask() {
        List<RescueTaskEntity> single = List.of(
                new RescueTaskEntity("TASK-01", "Solo mission", 4.5, "Cluster-Alpha", "Recon", "PENDING", null)
        );

        AnalyticsResult result = fallbackService.computeAnalytics(single, "single-test", -1, false);

        assertNotNull(result);
        assertEquals(1, result.getTotalTasks());
        assertEquals(4.5, result.getTotalWorkload(), 0.001);
        assertEquals(1, result.getBreakdown().size());
        assertEquals("Cluster-Alpha", result.getBreakdown().get(0).getCluster());
        assertEquals("Recon", result.getBreakdown().get(0).getTaskType());
        assertEquals(1, result.getBreakdown().get(0).getTaskCount());
        assertEquals(4.5, result.getBreakdown().get(0).getTotalWorkload(), 0.001);
    }

    @Test
    @DisplayName("Zero and negative workload tasks are defensively clamped to zero")
    void testZeroAndNegativeWorkloadDefensiveClamp() {
        List<RescueTaskEntity> edgeTasks = List.of(
                new RescueTaskEntity("T-ZERO", "Zero load task", 0.0, "Cluster-A", "Food", "PENDING", null),
                new RescueTaskEntity("T-NEG", "Negative load corrupted task", -5.0, "Cluster-A", "Food", "PENDING", null)
        );

        AnalyticsResult result = fallbackService.computeAnalytics(edgeTasks, "edge-workload", -1, false);

        assertNotNull(result);
        assertEquals(2, result.getTotalTasks());
        assertEquals(0.0, result.getTotalWorkload(), 0.001);
        assertEquals(0.0, result.getWorkloadPerCluster().get("Cluster-A"), 0.001);
        assertEquals(0.0, result.getWorkloadPerType().get("Food"), 0.001);
    }
}
