package com.drs.consensus.model;

import jakarta.persistence.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * An entity and record of one update to a resource, stamped with its vector clock.
 * <p>
 * {@code resourceType} is either {@code "STATUS"} (last-writer precedence + safety policy)
 * or {@code "SET_ADD"} (CRDT grow-only set, union on merge).
 * <p>
 * Direct port of legacy {@code common.UpdateRecord}, enhanced as a JPA Entity.
 */
@Entity
@Table(name = "update_record")
public class UpdateRecord {

    @Id
    @Column(name = "record_id", nullable = false)
    private String recordId;

    @Column(name = "resource_key", nullable = false)
    private String resourceKey;

    @Column(name = "resource_type", nullable = false)
    private String resourceType; // "STATUS" or "SET_ADD"

    @Column(name = "record_value", nullable = false)
    private String value;

    @Column(name = "cluster_id", nullable = false)
    private String clusterId;

    @Column(name = "node_id", nullable = false)
    private String nodeId;

    @Column(name = "description")
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "update_vector_clock", joinColumns = @JoinColumn(name = "record_id"))
    @MapKeyColumn(name = "node_id")
    @Column(name = "clock_value")
    private Map<String, Integer> vector = new HashMap<>();

    protected UpdateRecord() { }

    public UpdateRecord(String recordId, String resourceKey, String resourceType, String value,
                        String clusterId, String nodeId, String description,
                        Map<String, Integer> vector) {
        this.recordId = recordId;
        this.resourceKey = resourceKey;
        this.resourceType = resourceType;
        this.value = value;
        this.clusterId = clusterId;
        this.nodeId = nodeId;
        this.description = description;
        this.vector = vector != null ? new HashMap<>(vector) : new HashMap<>();
    }

    public String getRecordId()    { return recordId; }
    public String getResourceKey() { return resourceKey; }
    public String getResourceType(){ return resourceType; }
    public String getValue()       { return value; }
    public String getClusterId()   { return clusterId; }
    public String getNodeId()      { return nodeId; }
    public String getDescription() { return description; }
    public Map<String, Integer> getVector() { return Collections.unmodifiableMap(vector); }

    @Override
    public String toString() {
        return recordId + " | " + resourceKey + " | " + resourceType
                + " | " + value + " | " + nodeId + " | vc=" + vector;
    }
}
