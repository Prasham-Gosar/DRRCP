package com.drs.compute.service;

import com.drs.compute.model.AssignmentResult;
import com.drs.compute.model.LoadNode;
import com.drs.compute.model.NodeTrace;
import com.drs.compute.model.RescueTask;
import com.drs.compute.model.RescueTaskEntity;
import com.drs.compute.repository.RescueTaskRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LoadBalancerService}.
 * Verifies core requirements:
 * 1. Overloaded / depleted node skipped (effectiveCapacity <= 0)
 * 2. Unavailable node skipped (isAvailable() == false)
 * 3. Tie handling (first evaluated node wins on equal projected utilization)
 * 4. No available node found
 * 5. Full legacy demo sequence execution and exact distribution
 * 6. Live event publishing to EventPublisher
 * 7. assignAllPending sequentially assigning repository tasks
 * 8. Dynamic node availability toggle and re-balancing
 */
@ExtendWith(MockitoExtension.class)
class LoadBalancerServiceTest {

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
    @DisplayName("Overloaded or depleted node (effectiveCapacity <= 0) is skipped")
    void testOverloadedNodeSkipped() {
        LoadNode overloadedNode = new LoadNode("UAV-OVERLOADED", 0, 0, 10.0);
        LoadNode healthyNode = new LoadNode("UAV-HEALTHY", 80, 70, 10.0);

        List<LoadNode> nodes = new ArrayList<>(List.of(overloadedNode, healthyNode));
        RescueTask task = new RescueTask("TASK-01", "Deliver first aid", 3.0);

        AssignmentResult result = service.assignTask(nodes, task);

        assertTrue(result.isSuccess(), "Task should be successfully assigned to the healthy node");
        assertEquals("UAV-HEALTHY", result.getChosenNodeId());
        assertEquals(3.0, healthyNode.getCurrentLoad());
        assertEquals(0.0, overloadedNode.getCurrentLoad(), "Overloaded node should not receive load");

        NodeTrace traceOverloaded = result.getEvaluations().stream()
                .filter(t -> t.getNodeId().equals("UAV-OVERLOADED"))
                .findFirst()
                .orElseThrow();
        assertTrue(traceOverloaded.isSkipped());
        assertTrue(traceOverloaded.getSkippedReason().contains("no effective capacity"));
    }

    @Test
    @DisplayName("Unavailable node (isAvailable == false) is skipped")
    void testUnavailableNodeSkipped() {
        LoadNode offlineNode = new LoadNode("UAV-OFFLINE", 90, 90, 10.0);
        offlineNode.setAvailable(false);

        LoadNode onlineNode = new LoadNode("UAV-ONLINE", 60, 50, 10.0);

        List<LoadNode> nodes = new ArrayList<>(List.of(offlineNode, onlineNode));
        RescueTask task = new RescueTask("TASK-02", "Assess perimeter", 2.0);

        AssignmentResult result = service.assignTask(nodes, task);

        assertTrue(result.isSuccess());
        assertEquals("UAV-ONLINE", result.getChosenNodeId());
        assertEquals(2.0, onlineNode.getCurrentLoad());
        assertEquals(0.0, offlineNode.getCurrentLoad());

        NodeTrace traceOffline = result.getEvaluations().stream()
                .filter(t -> t.getNodeId().equals("UAV-OFFLINE"))
                .findFirst()
                .orElseThrow();
        assertTrue(traceOffline.isSkipped());
        assertTrue(traceOffline.getSkippedReason().contains("unavailable"));
    }

    @Test
    @DisplayName("Tie handling: lowest projected utilization retains the first encountered node")
    void testTieHandling() {
        LoadNode nodeA = new LoadNode("UAV-A", 80, 80, 10.0);
        LoadNode nodeB = new LoadNode("UAV-B", 80, 80, 10.0);

        List<LoadNode> nodes = new ArrayList<>(List.of(nodeA, nodeB));
        RescueTask task = new RescueTask("TASK-03", "Search area", 2.0);

        AssignmentResult result = service.assignTask(nodes, task);

        assertTrue(result.isSuccess());
        assertEquals("UAV-A", result.getChosenNodeId(), "First node evaluated should win on a tie");
        assertEquals(2.0, nodeA.getCurrentLoad());
        assertEquals(0.0, nodeB.getCurrentLoad());
    }

    @Test
    @DisplayName("No available node returns failure and null chosen node")
    void testNoAvailableNode() {
        LoadNode offlineNode = new LoadNode("UAV-01", 80, 80, 10.0);
        offlineNode.setAvailable(false);

        LoadNode depletedNode = new LoadNode("UAV-02", 0, 0, 10.0);

        List<LoadNode> nodes = new ArrayList<>(List.of(offlineNode, depletedNode));
        RescueTask task = new RescueTask("TASK-04", "Heavy transport", 5.0);

        AssignmentResult result = service.assignTask(nodes, task);

        assertFalse(result.isSuccess(), "Assignment should fail when no candidate is eligible");
        assertNull(result.getChosenNodeId());
        assertEquals(2, result.getEvaluations().size());
        assertTrue(result.getEvaluations().stream().allMatch(NodeTrace::isSkipped));
    }

    @Test
    @DisplayName("Executes exact legacy LoadBalancingDemo sequence and node failure simulation")
    void testLegacyDemoSequence() {
        LoadNode uav1 = new LoadNode("UAV-01", 80, 70, 10.0);
        LoadNode uav2 = new LoadNode("UAV-02", 60, 55, 10.0);
        LoadNode edge1 = new LoadNode("Edge-01", 90, 85, 15.0);

        List<LoadNode> nodes = new ArrayList<>(List.of(uav1, uav2, edge1));

        List<RescueTask> tasks = List.of(
                new RescueTask("TASK-01", "Search for survivors", 3.0),
                new RescueTask("TASK-02", "Inspect damaged building", 2.0),
                new RescueTask("TASK-03", "Deliver medical supplies", 4.0),
                new RescueTask("TASK-04", "Monitor rescue location", 2.0),
                new RescueTask("TASK-05", "Relay communication", 3.0),
                new RescueTask("TASK-06", "Search nearby area", 2.0)
        );

        List<AssignmentResult> results = service.assignAll(nodes, tasks);

        assertEquals(6, results.size());
        assertTrue(results.stream().allMatch(AssignmentResult::isSuccess));

        assertEquals("Edge-01", results.get(0).getChosenNodeId());
        assertEquals("UAV-01", results.get(1).getChosenNodeId());
        assertEquals("Edge-01", results.get(2).getChosenNodeId());

        uav1.setAvailable(false);
        RescueTask emergencyTask = new RescueTask("TASK-07", "Emergency survivor search", 2.0);
        AssignmentResult emergencyRes = service.assignTask(nodes, emergencyTask);

        assertTrue(emergencyRes.isSuccess());
        assertNotEquals("UAV-01", emergencyRes.getChosenNodeId(), "Failed UAV-01 must not be selected");
    }

    @Test
    @DisplayName("Publishes TASK_ASSIGNED events to EventPublisher")
    void testPublishesEvents() {
        LoadNode node = new LoadNode("UAV-01", 80, 80, 10.0);
        List<LoadNode> nodes = new ArrayList<>(List.of(node));
        RescueTask task = new RescueTask("TASK-TEST", "Test task", 1.0);

        service.assignTask(nodes, task);

        ArgumentCaptor<AssignmentResult> captor = ArgumentCaptor.forClass(AssignmentResult.class);
        verify(eventPublisher, atLeastOnce()).publish(eq("compute"), eq("TASK_ASSIGNED"), captor.capture());

        AssignmentResult captured = captor.getValue();
        assertEquals("TASK-TEST", captured.getTaskId());
        assertEquals("UAV-01", captured.getChosenNodeId());
    }

    @Test
    @DisplayName("assignAllPending sequentially assigns repository tasks and updates status")
    void testAssignAllPending() {
        RescueTaskEntity t1 = new RescueTaskEntity("TASK-01", "Task 1", 3.0, "Cluster-A", "Search", "PENDING", null);
        RescueTaskEntity t2 = new RescueTaskEntity("TASK-02", "Task 2", 2.0, "Cluster-A", "Search", "PENDING", null);
        RescueTaskEntity t3 = new RescueTaskEntity("TASK-03", "Task 3", 4.0, "Cluster-B", "Medical", "PENDING", null);

        when(taskRepository.findByStatus("PENDING")).thenReturn(new ArrayList<>(List.of(t1, t2, t3)));
        when(taskRepository.save(any(RescueTaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        List<AssignmentResult> results = service.assignAllPending();

        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(AssignmentResult::isSuccess));

        assertEquals("ASSIGNED", t1.getStatus());
        assertNotNull(t1.getAssignedNodeId());
        assertEquals("ASSIGNED", t2.getStatus());
        assertNotNull(t2.getAssignedNodeId());
        assertEquals("ASSIGNED", t3.getStatus());
        assertNotNull(t3.getAssignedNodeId());

        verify(taskRepository, times(3)).save(any(RescueTaskEntity.class));
        verify(eventPublisher, atLeast(3)).publish(eq("compute"), eq("TASK_ASSIGNED"), any());
    }

    @Test
    @DisplayName("assignAllPending returns empty list when no pending tasks exist")
    void testAssignAllPendingEmpty() {
        when(taskRepository.findByStatus("PENDING")).thenReturn(List.of());

        List<AssignmentResult> results = service.assignAllPending();

        assertTrue(results.isEmpty());
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Dynamic availability toggle skips unavailable node and selects alternative")
    void testNodeAvailabilityToggleAndRebalance() {
        // UAV-01 is initial default node
        service.setNodeAvailability("UAV-01", false);

        List<LoadNode> nodes = service.getCandidateNodes();
        LoadNode uav01 = nodes.stream().filter(n -> n.getNodeId().equals("UAV-01")).findFirst().orElseThrow();
        assertFalse(uav01.isAvailable());

        RescueTaskEntity entity = new RescueTaskEntity("TASK-FAILOVER", "Failover task", 2.0, "Cluster-A", "Search", "PENDING", null);
        when(taskRepository.findById("TASK-FAILOVER")).thenReturn(Optional.of(entity));
        when(taskRepository.save(any(RescueTaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AssignmentResult result = service.assignTaskEntity("TASK-FAILOVER");

        assertTrue(result.isSuccess());
        assertNotEquals("UAV-01", result.getChosenNodeId());
        assertEquals("ASSIGNED", entity.getStatus());
    }

    @Test
    @DisplayName("createTask generates default taskId and publishes TASK_CREATED event")
    void testCreateTask() {
        when(taskRepository.count()).thenReturn(5L);
        when(taskRepository.save(any(RescueTaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RescueTaskEntity toCreate = new RescueTaskEntity(null, "Emergency medical triage", 3.5, "Cluster-B", "Medical", null, null);
        RescueTaskEntity created = service.createTask(toCreate);

        assertNotNull(created.getTaskId());
        assertEquals("PENDING", created.getStatus());
        assertEquals("Emergency medical triage", created.getDescription());
        assertEquals(3.5, created.getWorkload());

        verify(taskRepository).save(any(RescueTaskEntity.class));
        verify(eventPublisher).publish(eq("compute"), eq("TASK_CREATED"), eq(created));
    }
}
