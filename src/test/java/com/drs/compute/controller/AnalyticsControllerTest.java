package com.drs.compute.controller;

import com.drs.compute.model.AnalyticsBreakdownItem;
import com.drs.compute.model.AnalyticsResult;
import com.drs.compute.model.AnalyticsRun;
import com.drs.compute.model.RescueTaskEntity;
import com.drs.compute.service.SparkJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@DisplayName("Exp 7: AnalyticsController REST Endpoints")
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SparkJobService sparkJobService;

    @Test
    @DisplayName("POST /api/analytics/mapreduce/run returns 200 with structured analytics result")
    void testRunAnalyticsEndpoint() throws Exception {
        AnalyticsResult mockResult = new AnalyticsResult(
                "DRS-MapReduceStreams",
                "2026-10-29T10:00:00Z",
                "rescue_tasks",
                10,
                41.0,
                Map.of("Cluster-A", 4),
                Map.of("Cluster-A", 14.0),
                Map.of("Medical", 4),
                Map.of("Medical", 14.0),
                List.of(new AnalyticsBreakdownItem("Cluster-A", "Medical", 2, 7.0)),
                "streams",
                25,
                false,
                List.of("[MAP] Start")
        );

        when(sparkJobService.runAnalytics(any())).thenReturn(mockResult);

        mockMvc.perform(post("/api/analytics/mapreduce/run")
                        .param("engine", "streams")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").value("DRS-MapReduceStreams"))
                .andExpect(jsonPath("$.totalTasks").value(10))
                .andExpect(jsonPath("$.totalWorkload").value(41.0))
                .andExpect(jsonPath("$.engine").value("streams"))
                .andExpect(jsonPath("$.countsPerCluster.Cluster-A").value(4))
                .andExpect(jsonPath("$.breakdown[0].cluster").value("Cluster-A"));
    }

    @Test
    @DisplayName("GET /api/analytics/mapreduce/latest returns latest analytics result")
    void testGetLatestEndpoint() throws Exception {
        AnalyticsResult mockResult = new AnalyticsResult(
                "DRS-SparkAnalytics",
                "2026-10-29T10:00:00Z",
                "rescue_tasks",
                5,
                20.0,
                Map.of("Cluster-B", 5),
                Map.of("Cluster-B", 20.0),
                Map.of("Search", 5),
                Map.of("Search", 20.0),
                List.of(),
                "spark",
                120,
                false,
                List.of()
        );

        when(sparkJobService.getLatestAnalytics()).thenReturn(Optional.of(mockResult));

        mockMvc.perform(get("/api/analytics/mapreduce/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("spark"))
                .andExpect(jsonPath("$.totalTasks").value(5))
                .andExpect(jsonPath("$.durationMs").value(120));
    }

    @Test
    @DisplayName("GET /api/analytics/mapreduce/history returns historical runs")
    void testGetHistoryEndpoint() throws Exception {
        AnalyticsRun run1 = new AnalyticsRun("spark", 300, 10, 41.0, "SUCCESS", "{}");
        run1.setId(1L);

        when(sparkJobService.getHistory()).thenReturn(List.of(run1));

        mockMvc.perform(get("/api/analytics/mapreduce/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].engine").value("spark"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /api/analytics/mapreduce/seed successfully seeds tasks")
    void testSeedEndpoint() throws Exception {
        when(sparkJobService.getOrSeedTasks()).thenReturn(List.of(
                new RescueTaskEntity("T1", "Task 1", 3.0, "Cluster-A", "Food", "PENDING", null)
        ));

        mockMvc.perform(post("/api/analytics/mapreduce/seed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.count").value(1));
    }
}

