public class SystemUpdate {

    private final String updateId;
    private final String resourceId;
    private final String updateType;
    private final String value;
    private final String sourceNode;
    private final long timestamp;

    public SystemUpdate(
            String updateId,
            String resourceId,
            String updateType,
            String value,
            String sourceNode) {

        this.updateId = updateId;
        this.resourceId = resourceId;
        this.updateType = updateType;
        this.value = value;
        this.sourceNode = sourceNode;
        this.timestamp = System.currentTimeMillis();
    }

    public String getUpdateId() {
        return updateId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getUpdateType() {
        return updateType;
    }

    public String getValue() {
        return value;
    }

    public String getSourceNode() {
        return sourceNode;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return updateId
                + " | Resource=" + resourceId
                + " | Type=" + updateType
                + " | Value=" + value
                + " | Source=" + sourceNode;
    }
}