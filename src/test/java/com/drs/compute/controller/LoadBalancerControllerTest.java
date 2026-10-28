package com.drs.compute.controller;

import com.drs.compute.model.AssignmentResult;
import com.drs.compute.model.LoadNode;
import com.drs.compute.model.RescueTaskEntity;
import com.drs.compute.service.LoadBalancerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * REST tests for {@link LoadBalancerController} (Exp 6 endpoints).
 */
@ExtendWith(MockitoExtension.class)
class LoadBalancerControllerTest {

    @Mock
    private LoadBalancerService loadBalancerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LoadBalancerController controller = new LoadBalancerController(loadBalancerService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/loadbalancer/nodes returns candidate nodes with capacity, load, utilization, and availability")
    void testGetNodes() throws Exception {
        LoadNode uav1 = new LoadNode("UAV-01", 80, 70, 10.0);
        LoadNode edge1 = new LoadNode("Edge-01", 90, 85, 15.0);

        when(loadBalancerService.getCandidateNodes()).thenReturn(List.of(uav1, edge1));

        mockMvc.perform(get("/api/loadbalancer/nodes"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nodeId").value("UAV-01"))
                .andExpect(jsonPath("$[0].capacity").value(10.0))
                .andExpect(jsonPath("$[0].effectiveCapacity").value(7.5))
                .andExpect(jsonPath("$[0].load").value(0.0))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[1].nodeId").value("Edge-01"))
                .andExpect(jsonPath("$[1].capacity").value(15.0));

        verify(loadBalancerService).getCandidateNodes();
    }

    @Test
    @DisplayName("POST /api/loadbalancer/assign-all returns list of assignment results")
    void testAssignAll() throws Exception {
        AssignmentResult res1 = new AssignmentResult("TASK-01", "Survivor search", 3.0);
        res1.setSuccess(true);
        res1.setChosenNodeId("Edge-01");
        res1.setUpdatedLoad(3.0);

        AssignmentResult res2 = new AssignmentResult("TASK-02", "Structural scan", 2.0);
        res2.setSuccess(true);
        res2.setChosenNodeId("UAV-01");
        res2.setUpdatedLoad(2.0);

        when(loadBalancerService.assignAllPending()).thenReturn(List.of(res1, res2));

        mockMvc.perform(post("/api/loadbalancer/assign-all"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].taskId").value("TASK-01"))
                .andExpect(jsonPath("$[0].chosenNodeId").value("Edge-01"))
                .andExpect(jsonPath("$[1].taskId").value("TASK-02"))
                .andExpect(jsonPath("$[1].chosenNodeId").value("UAV-01"));

        verify(loadBalancerService).assignAllPending();
    }

    @Test
    @DisplayName("POST /api/loadbalancer/nodes/{id}/available updates node availability")
    void testSetAvailability() throws Exception {
        LoadNode node = new LoadNode("UAV-01", 80, 70, 10.0);
        node.setAvailable(false);

        when(loadBalancerService.setNodeAvailability("UAV-01", false)).thenReturn(node);

        mockMvc.perform(post("/api/loadbalancer/nodes/UAV-01/available?available=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value("UAV-01"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));

        verify(loadBalancerService).setNodeAvailability("UAV-01", false);
    }

    @Test
    @DisplayName("POST /api/loadbalancer/reset resets nodes and returns fresh node list")
    void testResetNodes() throws Exception {
        LoadNode uav1 = new LoadNode("UAV-01", 80, 70, 10.0);
        when(loadBalancerService.resetNodes()).thenReturn(List.of(uav1));

        mockMvc.perform(post("/api/loadbalancer/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nodeId").value("UAV-01"));

        verify(loadBalancerService).resetNodes();
    }

    @Test
    @DisplayName("POST /api/loadbalancer/seed seeds sample rescue tasks")
    void testSeedTasks() throws Exception {
        RescueTaskEntity task = new RescueTaskEntity("TASK-01", "Search for survivors", 3.0, "Cluster-A", "Search", "PENDING", null);
        when(loadBalancerService.seedSampleTasks()).thenReturn(List.of(task));

        mockMvc.perform(post("/api/loadbalancer/seed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].taskId").value("TASK-01"));

        verify(loadBalancerService).seedSampleTasks();
    }
}

