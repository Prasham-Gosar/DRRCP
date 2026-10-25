import java.util.LinkedHashMap;
import java.util.Map;

public class BackupReplica {

    private final String nodeId;

    private boolean active;
    private boolean promoted;

    private final Map<String, SystemUpdate> replicatedUpdates;

    public BackupReplica(String nodeId) {

        this.nodeId = nodeId;
        this.active = true;
        this.promoted = false;

        this.replicatedUpdates = new LinkedHashMap<>();
    }

    // Receives an update from the Primary.
    public synchronized void replicate(SystemUpdate update) {

        if (!active) {
            System.out.println(
                    "[" + nodeId + "] Backup is unavailable."
            );
            return;
        }
        replicatedUpdates.put(
                update.getResourceId(),
                update
        );
        System.out.println(
                "[" + nodeId + "] Replicated update: "
                        + update
        );
    }

    // Backup becomes the new Primary after failure.
    public void promoteToPrimary() {
        if (!active) {
            System.out.println(
                    "[" + nodeId + "] Cannot promote. Backup is failed."
            );
            return;
        }
        promoted = true;
        System.out.println();
        System.out.println(
                "[" + nodeId + "] BACKUP PROMOTED TO PRIMARY"
        );
    }

    // Process a new system update after takeover.
    public synchronized void processNewUpdate(
            SystemUpdate update) {
        if (!active) {
            System.out.println(
                    "[" + nodeId + "] Node is unavailable."
            );
            return;
        }
        if (!promoted) {
            System.out.println(
                    "[" + nodeId
                            + "] Backup is not Primary yet."
            );
            return;
        }
        replicatedUpdates.put(
                update.getResourceId(),
                update
        );
        System.out.println(
                "[" + nodeId
                        + "] New update processed after failover: "
                        + update
        );
    }

    public void showState() {
        System.out.println();
        System.out.println(
                "[" + nodeId + "] Current System State"
        );

        System.out.println("--------------------------------------------");
        if (replicatedUpdates.isEmpty()) {
            System.out.println("No system updates available.");
            return;
        }
        for (SystemUpdate update : replicatedUpdates.values()) {
            System.out.println(update);
        }
    }
    public Map<String, SystemUpdate> getState() {
        return replicatedUpdates;
    }
    public boolean isPromoted() {
        return promoted;
    }
}