package com.drs.compute.controller;

import com.drs.compute.model.AssignmentResult;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * REST tests for {@link TaskController} (Exp 6 task endpoints).
 */
@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock
    private LoadBalancerService loadBalancerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TaskController controller = new TaskController(loadBalancerService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /api/tasks creates task in PENDING status and returns 201 Created")
    void testCreateTask() throws Exception {
        RescueTaskEntity saved = new RescueTaskEntity(
                "TASK-99", "Deliver emergency plasma", 3.0, "Cluster-A", "Medical", "PENDING", null
        );

        when(loadBalancerService.createTask(any(RescueTaskEntity.class))).thenReturn(saved);

        String json = """
                {
                  "taskId": "TASK-99",
                  "description": "Deliver emergency plasma",
                  "workload": 3.0,
                  "clusterId": "Cluster-A",
                  "type": "Medical"
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.taskId").value("TASK-99"))
                .andExpect(jsonPath("$.description").value("Deliver emergency plasma"))
                .andExpect(jsonPath("$.workload").value(3.0))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(loadBalancerService).createTask(any(RescueTaskEntity.class));
    }

    @Test
    @DisplayName("GET /api/tasks returns all rescue tasks")
    void testGetTasks() throws Exception {
        RescueTaskEntity t1 = new RescueTaskEntity("TASK-01", "Task 1", 2.0, "Cluster-A", "Search", "PENDING", null);
        RescueTaskEntity t2 = new RescueTaskEntity("TASK-02", "Task 2", 3.0, "Cluster-B", "Medical", "ASSIGNED", "Edge-01");

        when(loadBalancerService.getAllTasks(null, null)).thenReturn(List.of(t1, t2));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].taskId").value("TASK-01"))
                .andExpect(jsonPath("$[1].taskId").value("TASK-02"))
                .andExpect(jsonPath("$[1].assignedNodeId").value("Edge-01"));

        verify(loadBalancerService).getAllTasks(null, null);
    }

    @Test
    @DisplayName("POST /api/tasks/{id}/assign assigns single task and returns AssignmentResult")
    void testAssignSingleTask() throws Exception {
        AssignmentResult result = new AssignmentResult("TASK-01", "Survivor search", 3.0);
        result.setSuccess(true);
        result.setChosenNodeId("Edge-01");
        result.setUpdatedLoad(3.0);

        when(loadBalancerService.assignTaskEntity("TASK-01")).thenReturn(result);

        mockMvc.perform(post("/api/tasks/TASK-01/assign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("TASK-01"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.chosenNodeId").value("Edge-01"));

        verify(loadBalancerService).assignTaskEntity("TASK-01");
    }

    @Test
    @DisplayName("POST /api/tasks/seed calls seedSampleTasks")
    void testSeedTasks() throws Exception {
        RescueTaskEntity sample = new RescueTaskEntity("TASK-01", "Sample", 1.0, "Cluster-A", "Search", "PENDING", null);
        when(loadBalancerService.seedSampleTasks()).thenReturn(List.of(sample));

        mockMvc.perform(post("/api/tasks/seed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(loadBalancerService).seedSampleTasks();
    }

    @Test
    @DisplayName("DELETE /api/tasks deletes all tasks")
    void testDeleteAllTasks() throws Exception {
        mockMvc.perform(delete("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(loadBalancerService).deleteAllTasks();
    }
}

