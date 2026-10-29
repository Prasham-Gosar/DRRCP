package com.drs.compute.controller;

import com.drs.compute.model.AnalyticsResult;
import com.drs.compute.model.AnalyticsRun;
import com.drs.compute.model.RescueTaskEntity;
import com.drs.compute.service.SparkJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for MapReduce analytics operations (Exp 7).
 */
@RestController
@RequestMapping("/api/analytics/mapreduce")
public class AnalyticsController {

    private final SparkJobService sparkJobService;

    @Autowired
    public AnalyticsController(SparkJobService sparkJobService) {
        this.sparkJobService = sparkJobService;
    }

    public record RunAnalyticsRequest(String engine) {}

    /**
     * POST /api/analytics/mapreduce/run
     * Executes MapReduce analytics using Spark (if available) or Java Streams fallback.
     * Optional body or query param: engine=spark|streams
     */
    @PostMapping("/run")
    public ResponseEntity<AnalyticsResult> runAnalytics(
            @RequestParam(required = false) String engine,
            @RequestBody(required = false) RunAnalyticsRequest body) {

        String selectedEngine = null;
        if (body != null && body.engine() != null && !body.engine().isBlank()) {
            selectedEngine = body.engine();
        } else if (engine != null && !engine.isBlank()) {
            selectedEngine = engine;
        }

        AnalyticsResult result = sparkJobService.runAnalytics(selectedEngine);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/analytics/mapreduce/latest
     * Retrieves the most recent MapReduce analytics result.
     * If no prior run exists, automatically executes a run and returns the result.
     */
    @GetMapping("/latest")
    public ResponseEntity<AnalyticsResult> getLatest() {
        return sparkJobService.getLatestAnalytics()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(sparkJobService.runAnalytics(null)));
    }

    /**
     * GET /api/analytics/mapreduce/history
     * Retrieves all persisted historical runs ordered with the most recent first.
     */
    @GetMapping("/history")
    public ResponseEntity<List<AnalyticsRun>> getHistory() {
        return ResponseEntity.ok(sparkJobService.getHistory());
    }

    /**
     * POST /api/analytics/mapreduce/seed
     * Seeds or restores sample disaster relief rescue tasks.
     */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedTasks() {
        List<RescueTaskEntity> tasks = sparkJobService.getOrSeedTasks();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "count", tasks.size(),
                "message", "Disaster relief tasks successfully verified and seeded"
        ));
    }
}
