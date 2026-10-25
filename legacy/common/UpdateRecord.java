package common;

import java.io.Serializable;
import java.util.Map;

public class UpdateRecord implements Serializable {
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

    public String getRecordId() { return recordId; }
    public String getResourceKey() { return resourceKey; }
    public String getResourceType() { return resourceType; }
    public String getValue() { return value; }
    public String getClusterId() { return clusterId; }
    public String getNodeId() { return nodeId; }
    public String getDescription() { return description; }
    public Map<String, Integer> getVector() { return vector; }
}
