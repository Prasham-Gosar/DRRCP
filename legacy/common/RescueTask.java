package common;

public class RescueTask {
    private final String taskId;
    private final String description;
    private final double workload;

    public RescueTask(
            String taskId,
            String description,
            double workload) {
        this.taskId = taskId;
        this.description = description;
        this.workload = workload;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getDescription() {
        return description;
    }

    public double getWorkload() {
        return workload;
    }

    @Override
    public String toString() {
        return taskId + " | " + description + " | Workload=" + workload;
    }
}