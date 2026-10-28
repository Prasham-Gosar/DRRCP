package com.drs.consensus.controller;

import com.drs.consensus.model.UpdateRecord;
import com.drs.consensus.reconcile.UpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateControllerTest {

    @Mock
    UpdateService updateService;

    UpdateController controller;

    @BeforeEach
    void setUp() {
        controller = new UpdateController(updateService);
    }

    @Test
    @DisplayName("POST /api/updates stamps and returns record")
    void createUpdate() {
        UpdateRecord record = new UpdateRecord(
                "UPD-1", "House-12", "STATUS", "RESCUED",
                "Cluster-A", "UAV-01", "Notes", Map.of("UAV-01", 1)
        );

        when(updateService.recordUpdate(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(record);

        UpdateController.CreateUpdateRequest req = new UpdateController.CreateUpdateRequest(
                "House-12", "STATUS", "RESCUED", "Cluster-A", "UAV-01", "Notes"
        );

        ResponseEntity<UpdateRecord> resp = controller.createUpdate(req);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals("House-12", resp.getBody().getResourceKey());
    }

    @Test
    @DisplayName("GET /api/updates lists all records")
    void getAllUpdates() {
        UpdateRecord record = new UpdateRecord(
                "UPD-1", "House-12", "STATUS", "RESCUED",
                "Cluster-A", "UAV-01", "Notes", Map.of("UAV-01", 1)
        );
        when(updateService.getAllUpdates()).thenReturn(List.of(record));

        ResponseEntity<List<UpdateRecord>> resp = controller.getAllUpdates();
        assertEquals(200, resp.getStatusCode().value());
        assertEquals(1, resp.getBody().size());
    }

    @Test
    @DisplayName("DELETE /api/updates resets records")
    void resetUpdates() {
        doNothing().when(updateService).resetUpdates();
        ResponseEntity<Map<String, String>> resp = controller.resetUpdates();
        assertEquals(200, resp.getStatusCode().value());
        verify(updateService, times(1)).resetUpdates();
    }
}

