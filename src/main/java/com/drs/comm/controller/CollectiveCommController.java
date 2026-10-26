package com.drs.comm.controller;

import com.drs.comm.model.CollectiveResult;
import com.drs.comm.model.CommRequest;
import com.drs.comm.service.CollectiveCommService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for collective communication (Experiment 9).
 */
@RestController
@RequestMapping("/api/comm")
public class CollectiveCommController {

    private final CollectiveCommService commService;

    public CollectiveCommController(CollectiveCommService commService) {
        this.commService = commService;
    }

    /**
     * Executes the complete Experiment 9 collective communication workflow:
     * Broadcast -> Scatter -> Local deliveries -> Gather -> Verification.
     */
    @PostMapping("/run-all")
    public ResponseEntity<CollectiveResult> runAll(@RequestBody(required = false) CommRequest request) {
        CommRequest req = (request != null) ? request : new CommRequest();
        CollectiveResult result = commService.runAll(req);
        return ResponseEntity.ok(result);
    }

    /**
     * Executes MPI_Bcast: Root broadcasts identical emergency alert to all ranks.
     */
    @PostMapping("/bcast")
    public ResponseEntity<CollectiveResult> bcast(@RequestBody(required = false) CommRequest request) {
        CommRequest req = (request != null) ? request : new CommRequest();
        CollectiveResult result = commService.runBroadcast(req);
        return ResponseEntity.ok(result);
    }

    /**
     * Executes MPI_Scatter: Root splits supply packages evenly across ranks.
     */
    @PostMapping("/scatter")
    public ResponseEntity<CollectiveResult> scatter(@RequestBody(required = false) CommRequest request) {
        CommRequest req = (request != null) ? request : new CommRequest();
        CollectiveResult result = commService.runScatter(req);
        return ResponseEntity.ok(result);
    }

    /**
     * Executes MPI_Gather: Collects all delivery reports from ranks in rank order.
     */
    @PostMapping("/gather")
    public ResponseEntity<CollectiveResult> gather(@RequestBody(required = false) CommRequest request) {
        CommRequest req = (request != null) ? request : new CommRequest();
        CollectiveResult result = commService.runGather(req);
        return ResponseEntity.ok(result);
    }

    /**
     * Quick status check of collective comm capabilities and defaults.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "experiment", 9,
                "name", "MPI Collective Communication (Bcast, Scatter, Gather)",
                "defaultProcesses", 4,
                "defaultChunkSize", 2,
                "supportedOperations", new String[]{"BCAST", "SCATTER", "GATHER", "RUN_ALL"}
        ));
    }
}
