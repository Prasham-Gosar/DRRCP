package com.drs.consensus.model;

import java.util.Map;

/**
 * An immutable record of one update to a resource, stamped with its vector clock.
 * <p>
 * {@code resourceType} is either {@code "STATUS"} (last-writer precedence + safety policy)
 * or {@code "SET_ADD"} (CRDT grow-only set, union on merge).
 * <p>
 * Direct port of legacy {@code common.UpdateRecord}.
 */
public class UpdateRecord {

    private final String recordId;
    private final String resourceKey;
    private final String resourceType;   // "STATUS" or "SET_ADD"
    private final String value;
    private final String clusterId;
    private final String nodeId;
    private final String description;
    private final Map<String, Integer> vector;

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
        this.vector = vector;
    }

    public String getRecordId()    { return recordId; }
    public String getResourceKey() { return resourceKey; }
    public String getResourceType(){ return resourceType; }
    public String getValue()       { return value; }
    public String getClusterId()   { return clusterId; }
    public String getNodeId()      { return nodeId; }
    public String getDescription() { return description; }
    public Map<String, Integer> getVector() { return vector; }

    @Override
    public String toString() {
        return recordId + " | " + resourceKey + " | " + resourceType
                + " | " + value + " | " + nodeId + " | vc=" + vector;
    }
}
