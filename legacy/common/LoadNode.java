package common;

public class LoadNode {
    private final NodeStatus status;
    private final double maxCapacity;
    private double currentLoad;
    private boolean available;

    public LoadNode(NodeStatus status, double maxCapacity) {
        this.status = status;
        this.maxCapacity = maxCapacity;
        this.currentLoad = 0;
        this.available = true;
    }

    public String getNodeId() {
        return status.getNodeId();
    }

    public int getBatteryLevel() {
        return status.getBatteryLevel();
    }

    public int getSignalStrength() {
        return status.getSignalStrength();
    }

    public double getMaxCapacity() {
        return maxCapacity;
    }

    public double getCurrentLoad() {
        return currentLoad;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void addLoad(double workload) {
        currentLoad += workload;
    }

    public double getEffectiveCapacity() {
        double batteryFactor = getBatteryLevel() / 100.0;
        double signalFactor = getSignalStrength() / 100.0;
        double capacityFactor = (batteryFactor + signalFactor) / 2.0;
        return maxCapacity * capacityFactor;
    }

    public double getUtilization() {
        double capacity = getEffectiveCapacity();
        if (capacity <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return currentLoad / capacity;
    }

    @Override
    public String toString() {
        return getNodeId()
                + " | Battery=" + getBatteryLevel()
                + "% | Signal=" + getSignalStrength()
                + "% | Load=" + currentLoad
                + " | Effective Capacity="
                + String.format("%.2f", getEffectiveCapacity());
    }
}