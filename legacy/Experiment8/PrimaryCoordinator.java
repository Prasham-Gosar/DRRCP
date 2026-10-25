public class PrimaryCoordinator {

    private final String nodeId;

    private boolean active;

    private final BackupReplica backup;

    public PrimaryCoordinator(
            String nodeId,
            BackupReplica backup) {

        this.nodeId = nodeId;
        this.backup = backup;
        this.active = true;
    }

    // Primary receives a system update.
    public void processUpdate(SystemUpdate update) {
        if (!active) {
            System.out.println(
                    "[" + nodeId
                            + "] PRIMARY FAILED. "
                            + "Cannot process update."
            );
            return;
        }
        System.out.println(
                "[" + nodeId
                        + "] Processing system update: "
                        + update
        );

        // Replicate the update immediately to the Backup.
        backup.replicate(update);
        System.out.println(
                "[" + nodeId
                        + "] Update successfully replicated."
        );
    }

    // Simulate failure of the Primary.
    public void fail() {
        active = false;
        System.out.println();
        System.out.println(
                "       PRIMARY FAILURE        "
        );
        System.out.println(
                "[" + nodeId + "] Coordinator has FAILED."
        );
        System.out.println(
                "System will perform failover..."
        );
    }
    public boolean isActive() {
        return active;
    }
    public String getNodeId() {
        return nodeId;
    }
}