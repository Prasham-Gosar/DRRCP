package common;

import java.io.Serializable;

public class NodeStatus implements Serializable {
    private final String nodeId;
    private final int batteryLevel;
    private final int signalStrength;
    private final String nodeType;
    private final String clusterId;

    public NodeStatus(String nodeId, int batteryLevel, int signalStrength,
                       String nodeType, String clusterId) {
        this.nodeId = nodeId;
        this.batteryLevel = batteryLevel;
        this.signalStrength = signalStrength;
        this.nodeType = nodeType;
        this.clusterId = clusterId;
    }

    public String getNodeId() { return nodeId; }
    public int getBatteryLevel() { return batteryLevel; }
    public int getSignalStrength() { return signalStrength; }
    public String getNodeType() { return nodeType; }
    public String getClusterId() { return clusterId; }
}
