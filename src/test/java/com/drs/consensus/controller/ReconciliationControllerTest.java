package com.drs.consensus.controller;

import com.drs.consensus.model.ReconciliationResult;
import com.drs.consensus.model.Resolution;
import com.drs.consensus.reconcile.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationControllerTest {

    @Mock
    ReconciliationService reconciliationService;

    ReconciliationController controller;

    @BeforeEach
    void setUp() {
        controller = new ReconciliationController(reconciliationService);
    }

    @Test
    @DisplayName("POST /api/reconcile runs reconciliation and returns result")
    void reconcile() {
        Resolution res = new Resolution(
                "House-12", "STATUS", "NEEDS_RESCUE", "POLICY_RESOLVED", true, List.of(), "Conflict resolved"
        );
        ReconciliationResult result = new ReconciliationResult(List.of(res), List.of("step 1"), 2, 1, 1);

        when(reconciliationService.reconcileAll()).thenReturn(result);

        ResponseEntity<ReconciliationResult> resp = controller.reconcile();
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().getResolutions().size());
        assertEquals("NEEDS_RESCUE", resp.getBody().getResolutions().get(0).getFinalValue());
        verify(reconciliationService, times(1)).reconcileAll();
    }

    @Test
    @DisplayName("GET /api/reconcile/latest returns latest result")
    void getLatest() {
        ReconciliationResult result = new ReconciliationResult(List.of(), List.of(), 0, 0, 0);
        when(reconciliationService.getLatest()).thenReturn(result);

        ResponseEntity<ReconciliationResult> resp = controller.getLatest();
        assertEquals(200, resp.getStatusCode().value());
        assertEquals(0, resp.getBody().getTotalUpdates());
        verify(reconciliationService, times(1)).getLatest();
    }
}

