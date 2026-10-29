package com.drs.compute.service;

import com.drs.compute.model.AssignmentResult;
import com.drs.compute.model.LoadNode;
import com.drs.compute.model.RescueTaskEntity;
import com.drs.compute.repository.RescueTaskRepository;
import com.drs.consensus.LeaderProvider;
import com.drs.core.event.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Exp 6+7: ClusterTaskService Pipeline & Leader Allocation")
class ClusterTaskServiceTest {

    @Mock
    private RescueTaskRepository taskRepository;

    @Mock
    private LoadBalancerService loadBalancerService;

    @Mock
    private LeaderProvider leaderProvider;

    @Mock
    private EventPublisher eventPublisher;

    private ClusterTaskService clusterTaskService;

    @BeforeEach
    void setUp() {
        clusterTaskService = new ClusterTaskService(
                taskRepository,
                loadBalancerService,
                leaderProvider,
                eventPublisher
        );

        lenient().when(taskRepository.save(any(RescueTaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Successfully generates and assigns partitioned tasks strictly to local cluster nodes via elected leaders")
    void testGenerateAndAssignPartitionedTasks() {
        // Mock elected leaders
        when(leaderProvider.currentLeader("Cluster-A")).thenReturn("UAV-02");
        when(leaderProvider.currentLeader("Cluster-B")).thenReturn("Edge-01");

        // Mock fleet nodes
        LoadNode uav1 = new LoadNode("UAV-01", 85, 75, 10.0);
        uav1.setClusterId("Cluster-A");
        LoadNode uav2 = new LoadNode("UAV-02", 60, 50, 10.0);
        uav2.setClusterId("Cluster-A");

        LoadNode edge1 = new LoadNode("Edge-01", 95, 90, 15.0);
        edge1.setClusterId("Cluster-B");
        edge1.setNodeType("EDGE");
        LoadNode edge2 = new LoadNode("Edge-02", 70, 65, 15.0);
        edge2.setClusterId("Cluster-B");
        edge2.setNodeType("EDGE");

        when(loadBalancerService.getCandidateNodes()).thenReturn(List.of(uav1, uav2, edge1, edge2));

        List<AssignmentResult> results = clusterTaskService.generateAndAssignPartitionedTasks();

        assertNotNull(results);
        assertEquals(6, results.size());

        // Verify Cluster-A tasks assigned by UAV-02 to Cluster-A nodes only
        List<AssignmentResult> clusterAResults = results.subList(0, 3);
        for (AssignmentResult r : clusterAResults) {
            assertTrue(r.isSuccess());
            assertEquals("Cluster-A", r.getClusterId());
            assertEquals("UAV-02", r.getAssignedByLeaderId());
            assertTrue(List.of("UAV-01", "UAV-02").contains(r.getChosenNodeId()));
        }

        // Verify Cluster-B tasks assigned by Edge-01 to Cluster-B nodes only
        List<AssignmentResult> clusterBResults = results.subList(3, 6);
        for (AssignmentResult r : clusterBResults) {
            assertTrue(r.isSuccess());
            assertEquals("Cluster-B", r.getClusterId());
            assertEquals("Edge-01", r.getAssignedByLeaderId());
            assertTrue(List.of("Edge-01", "Edge-02").contains(r.getChosenNodeId()));
        }

        // Verify live SSE events emitted for every task
        verify(eventPublisher, times(6)).publish(eq("compute"), eq("TASK_ASSIGNED"), any());
    }

    @Test
    @DisplayName("Falls back to default cluster leaders when LeaderProvider returns null")
    void testFallbackWhenLeaderProviderNull() {
        when(leaderProvider.currentLeader(anyString())).thenReturn(null);

        LoadNode uav1 = new LoadNode("UAV-01", 85, 75, 10.0);
        uav1.setClusterId("Cluster-A");
        LoadNode edge1 = new LoadNode("Edge-01", 95, 90, 15.0);
        edge1.setClusterId("Cluster-B");

        when(loadBalancerService.getCandidateNodes()).thenReturn(List.of(uav1, edge1));

        List<AssignmentResult> results = clusterTaskService.generateAndAssignPartitionedTasks();

        assertEquals(6, results.size());
        assertEquals("UAV-02", results.get(0).getAssignedByLeaderId());
        assertEquals("Edge-01", results.get(3).getAssignedByLeaderId());
    }

    @Test
    @DisplayName("Generates aggregated pipeline summary")
    void testPipelineSummary() {
        when(leaderProvider.currentLeader("Cluster-A")).thenReturn("UAV-02");
        when(leaderProvider.currentLeader("Cluster-B")).thenReturn("Edge-01");

        List<RescueTaskEntity> tasks = List.of(
                new RescueTaskEntity("T1", "A1", 3.0, "Cluster-A", "Search", "ASSIGNED", "UAV-01", "UAV-02"),
                new RescueTaskEntity("T2", "B1", 5.0, "Cluster-B", "Medical", "ASSIGNED", "Edge-01", "Edge-01"),
                new RescueTaskEntity("T3", "A2", 2.0, "Cluster-A", "Food", "PENDING", null)
        );
        when(taskRepository.findAll()).thenReturn(tasks);

        Map<String, Object> summary = clusterTaskService.getPipelineSummary();

        assertNotNull(summary);
        assertEquals(3L, summary.get("totalTasks"));
        assertEquals(2L, summary.get("assignedTasks"));
        assertEquals(1L, summary.get("pendingTasks"));
        assertEquals(10.0, (Double) summary.get("totalWorkload"), 0.001);

        @SuppressWarnings("unchecked")
        Map<String, String> leaders = (Map<String, String>) summary.get("clusterLeaders");
        assertEquals("UAV-02", leaders.get("Cluster-A"));
        assertEquals("Edge-01", leaders.get("Cluster-B"));
    }
}

