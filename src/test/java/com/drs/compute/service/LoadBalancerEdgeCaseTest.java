package com.drs.compute.service;

import com.drs.compute.balancer.LoadBalancer;
import com.drs.compute.model.AssignmentResult;
import com.drs.compute.model.LoadNode;
import com.drs.compute.model.RescueTask;
import com.drs.compute.model.RescueTaskEntity;
import com.drs.compute.repository.RescueTaskRepository;
import com.drs.core.event.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Exp 6: LoadBalancer Edge Cases and Hardening")
class LoadBalancerEdgeCaseTest {

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private RescueTaskRepository taskRepository;

    private LoadBalancerService service;

    @BeforeEach
    void setUp() {
        service = new LoadBalancerService(eventPublisher, taskRepository, null);
    }

    @Test
    @DisplayName("When all candidate nodes are unavailable, assignTask returns success=false with clear steps")
    void testAllNodesUnavailableAssignTask() {
        LoadNode n1 = new LoadNode("UAV-01", 80, 80, 10.0);
        n1.setAvailable(false);
        LoadNode n2 = new LoadNode("UAV-02", 70, 70, 10.0);
        n2.setAvailable(false);

        RescueTask task = new RescueTask("T-CRITICAL", "Emergency mission", 5.0);
        AssignmentResult result = service.assignTask(List.of(n1, n2), task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNull(result.getChosenNodeId());
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("No available node found")));
        assertEquals(2, result.getEvaluations().size());
        assertTrue(result.getEvaluations().stream().allMatch(e -> e.isSkipped() && e.getSkipReason().contains("unavailable")));
    }

    @Test
    @DisplayName("When all nodes are unavailable in assignAllPending, tasks remain pending and results report failure")
    void testAllNodesUnavailableAssignAllPending() {
        // Mark all active candidate nodes unavailable
        for (LoadNode node : service.getCandidateNodes()) {
            service.setNodeAvailability(node.getNodeId(), false);
        }

        RescueTaskEntity pendingTask = new RescueTaskEntity("TASK-P1", "Pending mission", 4.0, "Cluster-A", "Search", "PENDING", null);
        when(taskRepository.findByStatus("PENDING")).thenReturn(List.of(pendingTask));

        List<AssignmentResult> results = service.assignAllPending();

        assertEquals(1, results.size());
        assertFalse(results.get(0).isSuccess());
        assertEquals("PENDING", pendingTask.getStatus());
        assertNull(pendingTask.getAssignedNodeId());
        // Verify taskRepository.save is not called for failed assignment
        verify(taskRepository, never()).save(pendingTask);
    }

    @Test
    @DisplayName("Handles huge workload (e.g. 9999.0) without numeric overflow or assignment error")
    void testHugeWorkloadAssignment() {
        RescueTask hugeTask = new RescueTask("T-HUGE", "Massive disaster response", 9999.0);
        List<LoadNode> nodes = service.getCandidateNodes();

        AssignmentResult result = service.assignTask(nodes, hugeTask);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getChosenNodeId());
        assertTrue(result.getUpdatedLoad() >= 9999.0);
        assertTrue(result.getFinalUtilization() > 100.0);
    }

    @Test
    @DisplayName("When repository has zero tasks, getAllTasks returns an empty list gracefully")
    void testZeroTasksInDatabase() {
        when(taskRepository.findAll()).thenReturn(Collections.emptyList());

        List<RescueTaskEntity> tasks = service.getAllTasks(null, null, null);

        assertNotNull(tasks);
        assertTrue(tasks.isEmpty());
    }

    @Test
    @DisplayName("When candidate nodes have zero effective capacity, they are skipped and assignment fails cleanly")
    void testZeroEffectiveCapacitySkipped() {
        LoadNode deadBattery = new LoadNode("DEAD-01", 0, 0, 10.0); // effCap = 0
        LoadBalancer balancer = new LoadBalancer(List.of(deadBattery));

        RescueTask task = new RescueTask("T1", "Task 1", 3.0);
        AssignmentResult result = balancer.assignTask(task);

        assertFalse(result.isSuccess());
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("no effective capacity")));
    }
}
