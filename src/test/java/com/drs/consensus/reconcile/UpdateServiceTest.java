package com.drs.consensus.reconcile;

import com.drs.consensus.DefaultNetworkPartitionStatus;
import com.drs.consensus.model.UpdateRecord;
import com.drs.consensus.model.VectorClock;
import com.drs.consensus.repository.UpdateRecordRepository;
import com.drs.core.event.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateServiceTest {

    @Mock
    UpdateRecordRepository repository;

    @Mock
    EventPublisher eventPublisher;

    VectorClockRegistry clockRegistry;
    ReconciliationService reconciliationService;
    DefaultNetworkPartitionStatus partitionStatus;
    UpdateService updateService;

    private List<UpdateRecord> inMemoryStore;

    @BeforeEach
    void setUp() {
        clockRegistry = new VectorClockRegistry();
        reconciliationService = new ReconciliationService(eventPublisher);
        partitionStatus = new DefaultNetworkPartitionStatus();
        partitionStatus.setPartitioned(true);

        inMemoryStore = new ArrayList<>();
        lenient().when(repository.save(any(UpdateRecord.class))).thenAnswer(invocation -> {
            UpdateRecord r = invocation.getArgument(0);
            inMemoryStore.add(r);
            return r;
        });
        lenient().when(repository.findAll()).thenAnswer(invocation -> inMemoryStore);
        lenient().when(repository.findAllByOrderByRecordIdAsc()).thenAnswer(invocation -> inMemoryStore);

        updateService = new UpdateService(
                repository, clockRegistry, reconciliationService, eventPublisher, partitionStatus
        );
    }

    @Test
    @DisplayName("Recording an update ticks vector clock and persists record")
    void recordUpdateTicksClock() {
        UpdateRecord rec = updateService.recordUpdate(
                "House-12", "STATUS", "RESCUED", "Cluster-A", "UAV-01", "Clear"
        );

        assertNotNull(rec);
        assertEquals("House-12", rec.getResourceKey());
        assertEquals("RESCUED", rec.getValue());
        assertEquals("UAV-01", rec.getNodeId());
        assertEquals(1, rec.getVector().get("UAV-01"));
        verify(repository, times(1)).save(any(UpdateRecord.class));
    }

    @Test
    @DisplayName("Partitioned clusters produce concurrent vector clocks for House-12")
    void partitionedClustersProduceConcurrentClocks() {
        // UAV-01 in Cluster-A
        UpdateRecord u1 = updateService.recordUpdate(
                "House-12", "STATUS", "RESCUED", "Cluster-A", "UAV-01", "Clear"
        );

        // UAV-02 in Cluster-B
        UpdateRecord u2 = updateService.recordUpdate(
                "House-12", "STATUS", "NEEDS_RESCUE", "Cluster-B", "UAV-02", "Trapped people"
        );

        // Clocks must be concurrent: [UAV-01: 1, UAV-02: 0] vs [UAV-01: 0, UAV-02: 1]
        VectorClock.Relation rel = VectorClock.compare(u1.getVector(), u2.getVector());
        assertEquals(VectorClock.Relation.CONCURRENT, rel, "Partitioned updates must be concurrent");
    }

    @Test
    @DisplayName("Nodes within the same communicating cluster merge clocks causally")
    void intraClusterUpdatesMergeClocks() {
        // UAV-01 in Cluster-A
        UpdateRecord u1 = updateService.recordUpdate(
                "House-12", "STATUS", "RESCUED", "Cluster-A", "UAV-01", "Clear"
        );

        // Edge-01 also in Cluster-A (can communicate)
        UpdateRecord u2 = updateService.recordUpdate(
                "House-12", "STATUS", "NEEDS_RESCUE", "Cluster-A", "Edge-01", "Update"
        );

        // Edge-01 merges UAV-01's clock [UAV-01: 1], then ticks Edge-01: [UAV-01: 1, Edge-01: 1]
        VectorClock.Relation rel = VectorClock.compare(u1.getVector(), u2.getVector());
        assertEquals(VectorClock.Relation.BEFORE, rel, "Earlier cluster update must happen-before later cluster update");
    }

    @Test
    @DisplayName("Seed scenario populates partition updates")
    void seedScenarioPopulates() {
        List<UpdateRecord> records = updateService.seedScenarioUpdates();
        assertFalse(records.isEmpty());
        assertTrue(records.stream().anyMatch(r -> "House-12".equals(r.getResourceKey())));
        assertTrue(records.stream().anyMatch(r -> "Zone-3-Survivors".equals(r.getResourceKey())));
    }
}

