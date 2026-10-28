package com.drs.consensus.controller;

import com.drs.consensus.model.ReconciliationResult;
import com.drs.consensus.reconcile.ReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Exp 5 Vector-Clock and CRDT Reconciliation.
 */
@RestController
@RequestMapping("/api/reconcile")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * POST /api/reconcile — Execute reconciliation on all stored updates.
     */
    @PostMapping
    public ResponseEntity<ReconciliationResult> reconcile() {
        ReconciliationResult result = reconciliationService.reconcileAll();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/reconcile/latest — Get the most recent reconciliation result.
     */
    @GetMapping("/latest")
    public ResponseEntity<ReconciliationResult> getLatest() {
        return ResponseEntity.ok(reconciliationService.getLatest());
    }
}

