package com.drs.compute.service;

import com.drs.compute.model.AssignmentResult;
import com.drs.compute.model.LoadNode;
import com.drs.compute.model.NodeTrace;
import com.drs.compute.model.RescueTask;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link LoadBalancerService}.
 * Verifies core requirements:
 * 1. Overloaded / depleted node skipped (effectiveCapacity <= 0)
 * 2. Unavailable node skipped (isAvailable() == false)
 * 3. Tie handling (first evaluated node wins on equal projected utilization)
 * 4. No available node found
 * 5. Full legacy demo sequence execution and exact distribution
 * 6. Live event publishing to EventPublisher
 */
@ExtendWith(MockitoExtension.class)
class LoadBalancerServiceTest {

    @Mock
    private EventPublisher eventPublisher;

    private LoadBalancerService service;

    @BeforeEach
    void setUp() {
        service = new LoadBalancerService(eventPublisher);
    }

    @Test
    @DisplayName("Overloaded or depleted node (effectiveCapacity <= 0) is skipped")
    void testOverloadedNodeSkipped() {
        // Node 1 has battery=0, signal=0 -> effective capacity = 0.0 (overloaded/exhausted)
        LoadNode overloadedNode = new LoadNode("UAV-OVERLOADED", 0, 0, 10.0);
        // Node 2 has healthy battery and signal
        LoadNode healthyNode = new LoadNode("UAV-HEALTHY", 80, 70, 10.0);

        List<LoadNode> nodes = new ArrayList<>(List.of(overloadedNode, healthyNode));
        RescueTask task = new RescueTask("TASK-01", "Deliver first aid", 3.0);

        AssignmentResult result = service.assignTask(nodes, task);

        assertTrue(result.isSuccess(), "Task should be successfully assigned to the healthy node");
        assertEquals("UAV-HEALTHY", result.getChosenNodeId());
        assertEquals(3.0, healthyNode.getCurrentLoad());
        assertEquals(0.0, overloadedNode.getCurrentLoad(), "Overloaded node should not receive load");

        // Verify traces
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
        // Both nodes have identical battery, signal, maxCapacity, and zero initial load
        LoadNode nodeA = new LoadNode("UAV-A", 80, 80, 10.0);
        LoadNode nodeB = new LoadNode("UAV-B", 80, 80, 10.0);

        List<LoadNode> nodes = new ArrayList<>(List.of(nodeA, nodeB));
        RescueTask task = new RescueTask("TASK-03", "Search area", 2.0);

        AssignmentResult result = service.assignTask(nodes, task);

        assertTrue(result.isSuccess());
        // Since projectedUtilization(A) == projectedUtilization(B), strict '<' preserves nodeA
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
        // Initial setup from legacy LoadBalancingDemo:
        // uav1: 80% bat, 70% sig, cap=10 -> effCap = 10 * 0.75 = 7.50
        // uav2: 60% bat, 55% sig, cap=10 -> effCap = 10 * 0.575 = 5.75
        // edge1: 90% bat, 85% sig, cap=15 -> effCap = 15 * 0.875 = 13.125
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

        // In legacy:
        // Task 1 (w=3): Edge-01 has 3/13.125 = 0.23 -> Edge-01 chosen (load 3)
        // Task 2 (w=2): UAV-01 has 2/7.5 = 0.27 -> UAV-01 chosen (load 2)
        // Task 3 (w=4): Edge-01 has (3+4)/13.125 = 0.53 -> Edge-01 chosen (load 7)
        assertEquals("Edge-01", results.get(0).getChosenNodeId());
        assertEquals("UAV-01", results.get(1).getChosenNodeId());
        assertEquals("Edge-01", results.get(2).getChosenNodeId());

        // Node failure simulation as in legacy demo:
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
}
