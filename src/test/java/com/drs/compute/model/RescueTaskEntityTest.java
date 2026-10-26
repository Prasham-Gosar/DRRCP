package com.drs.compute.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RescueTaskEntity} and its conversions.
 */
class RescueTaskEntityTest {

    @Test
    @DisplayName("Entity correctly stores fields and converts to RescueTask DTO")
    void testEntityPropertiesAndConversion() {
        RescueTaskEntity entity = new RescueTaskEntity(
                "TASK-101",
                "Deliver emergency medical kit",
                3.5,
                "Cluster-A",
                "Medical",
                "PENDING",
                null
        );

        assertEquals("TASK-101", entity.getTaskId());
        assertEquals("Deliver emergency medical kit", entity.getDescription());
        assertEquals(3.5, entity.getWorkload());
        assertEquals("Cluster-A", entity.getClusterId());
        assertEquals("Medical", entity.getType());
        assertEquals("PENDING", entity.getStatus());
        assertNull(entity.getAssignedNodeId());
        assertNotNull(entity.getCreatedAt());

        // Update to ASSIGNED
        entity.setStatus("ASSIGNED");
        entity.setAssignedNodeId("UAV-01");
        assertEquals("ASSIGNED", entity.getStatus());
        assertEquals("UAV-01", entity.getAssignedNodeId());
        assertNotNull(entity.getAssignedAt());

        // DTO conversion
        RescueTask dto = RescueTask.fromEntity(entity);
        assertNotNull(dto);
        assertEquals("TASK-101", dto.getTaskId());
        assertEquals("Deliver emergency medical kit", dto.getDescription());
        assertEquals(3.5, dto.getWorkload());
        assertEquals("Cluster-A", dto.getClusterId());
        assertEquals("Medical", dto.getType());
    }
}
