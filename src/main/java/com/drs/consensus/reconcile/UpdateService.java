package com.drs.consensus.reconcile;

import com.drs.consensus.DefaultNetworkPartitionStatus;
import com.drs.consensus.NetworkPartitionStatus;
import com.drs.consensus.model.UpdateRecord;
import com.drs.consensus.repository.UpdateRecordRepository;
import com.drs.core.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service managing resource updates and vector clock stamping (Exp 5).
 * <p>
 * During network partitions, nodes only merge clocks with other nodes
 * within their communicating partition component.
 */
@Service
public class UpdateService {

    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);

    private final UpdateRecordRepository repository;
    private final VectorClockRegistry clockRegistry;
    private final ReconciliationService reconciliationService;
    private final EventPublisher eventPublisher;
    private final NetworkPartitionStatus networkPartitionStatus;

    private final AtomicInteger counter = new AtomicInteger(0);

    @Autowired
    public UpdateService(UpdateRecordRepository repository,
                         VectorClockRegistry clockRegistry,
                         ReconciliationService reconciliationService,
                         EventPublisher eventPublisher,
                         @Autowired(required = false) NetworkPartitionStatus networkPartitionStatus) {
        this.repository = repository;
        this.clockRegistry = clockRegistry;
        this.reconciliationService = reconciliationService;
        this.eventPublisher = eventPublisher;
        this.networkPartitionStatus = (networkPartitionStatus != null)
                ? networkPartitionStatus
                : new DefaultNetworkPartitionStatus();
    }

    /**
     * Stamp an update with the node's vector clock and persist it.
     * Merges clock from reachable prior updates before ticking.
     */
    public synchronized UpdateRecord recordUpdate(String resourceKey, String resourceType, String value,
                                                  String clusterId, String nodeId, String description) {
        // Ensure vector clock exists for this node
        clockRegistry.ensureRegistered(nodeId, List.of("UAV-01", "UAV-02", "Edge-01", "Edge-02", "Base-01"));

        // Intra-cluster clock merge: merge vector clocks of updates in reachable clusters
        List<UpdateRecord> priorUpdates = repository.findAll();
        for (UpdateRecord prev : priorUpdates) {
            if (networkPartitionStatus.canCommunicate(clusterId, prev.getClusterId())) {
                clockRegistry.merge(nodeId, prev.getVector());
            }
        }

        // Advance logical clock for this node
        clockRegistry.tick(nodeId);
        Map<String, Integer> vcSnapshot = clockRegistry.snapshot(nodeId);

        String recordId = "UPD-" + counter.incrementAndGet();
        UpdateRecord record = new UpdateRecord(
                recordId, resourceKey, resourceType, value, clusterId, nodeId, description, vcSnapshot
        );

        repository.save(record);
        reconciliationService.addUpdate(record);

        eventPublisher.publish("consensus", "UPDATE_RECORDED", Map.of(
                "recordId", recordId,
                "resourceKey", resourceKey,
                "resourceType", resourceType,
                "value", value,
                "clusterId", clusterId,
                "nodeId", nodeId,
                "vector", vcSnapshot
        ));

        log.info("Recorded update {}: key={}, type={}, value={}, node={}, cluster={}, vc={}",
                recordId, resourceKey, resourceType, value, nodeId, clusterId, vcSnapshot);

        return record;
    }

    /**
     * Retrieve all stored updates in insertion order.
     */
    public List<UpdateRecord> getAllUpdates() {
        return repository.findAllByOrderByRecordIdAsc();
    }

    /**
     * Clear all stored updates and vector clocks.
     */
    public synchronized void resetUpdates() {
        repository.deleteAll();
        reconciliationService.clearUpdates();
        clockRegistry.clear();
        counter.set(0);

        eventPublisher.publish("consensus", "UPDATES_CLEARED", Map.of("status", "cleared"));
        log.info("Cleared all updates and reset vector clocks");
    }

    /**
     * Helper to seed standard disaster relief partition scenario updates:
     * - House-12: UAV-01 (Cluster-A) marks RESCUED vs UAV-02 (Cluster-B) marks NEEDS_RESCUE (concurrent conflict)
     * - Bridge-4: Edge-01 (Cluster-A) marks COLLAPSED (causal update)
     * - Survivors: UAV-01 finds ["John Doe"], UAV-02 finds ["Jane Smith"] (SET_ADD union)
     */
    public synchronized List<UpdateRecord> seedScenarioUpdates() {
        resetUpdates();
        List<UpdateRecord> list = new ArrayList<>();

        // House-12 update from Cluster-A
        list.add(recordUpdate("House-12", "STATUS", "RESCUED",
                "Cluster-A", "UAV-01", "UAV-01 aerial scan reports House-12 clear"));

        // House-12 concurrent update from Cluster-B
        list.add(recordUpdate("House-12", "STATUS", "NEEDS_RESCUE",
                "Cluster-B", "UAV-02", "UAV-02 heat sensor detected 2 people trapped"));

        // Survivors set update from Cluster-A
        list.add(recordUpdate("Zone-3-Survivors", "SET_ADD", "John Doe",
                "Cluster-A", "UAV-01", "Survivor identified via radio responder"));

        // Survivors set update from Cluster-B
        list.add(recordUpdate("Zone-3-Survivors", "SET_ADD", "Jane Smith",
                "Cluster-B", "UAV-02", "Survivor identified via visual signal"));

        // Bridge-4 status from Cluster-A
        list.add(recordUpdate("Bridge-4", "STATUS", "COLLAPSED",
                "Cluster-A", "Edge-01", "Bridge-4 structural inspection: collapsed"));

        return list;
    }
}

