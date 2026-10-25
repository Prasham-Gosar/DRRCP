public class FaultToleranceDemo {

    private static int updateCounter = 0;

    public static void main(String[] args) {

        System.out.println(
                " PRIMARY-BACKUP FAULT TOLERANCE"
        );
        System.out.println(
                " Disaster Relief Coordination System"
        );

        BackupReplica backup = new BackupReplica("Coordinator-Backup");
        PrimaryCoordinator primary = new PrimaryCoordinator("Coordinator-Primary", backup);
        System.out.println();
        System.out.println( "STEP 1: PRIMARY-BACKUP SYSTEM INITIALIZED");

        System.out.println(
                "Primary : Coordinator-Primary"
        );

        System.out.println(
                "Backup  : Coordinator-Backup"
        );

        System.out.println();
        System.out.println(
                "STEP 2: PROCESSING NORMAL SYSTEM UPDATES"
        );
        primary.processUpdate(
                createUpdate(
                        "House-12",
                        "STATUS",
                        "NEEDS_RESCUE",
                        "UAV-01"
                )
        );
        primary.processUpdate(
                createUpdate(
                        "Hospital-01",
                        "STATUS",
                        "MEDICAL_ASSISTANCE",
                        "UAV-02"
                )
        );
        primary.processUpdate(
                createUpdate(
                        "Shelter-03",
                        "STATUS",
                        "OPEN",
                        "Edge-01"
                )
        );
        primary.processUpdate(
                createUpdate(
                        "House-15",
                        "STATUS",
                        "RESCUED",
                        "UAV-01"
                )
        );
        System.out.println();
        System.out.println(
                "STEP 3: BACKUP REPLICA STATE BEFORE FAILURE"
        );
        backup.showState();
        System.out.println();
        System.out.println(
                "STEP 4: SIMULATING PRIMARY FAILURE"
        );
        primary.fail();


        // Try sending an update to failed Primary
        System.out.println();
        System.out.println(
                "Attempting to send update to failed Primary..."
        );
        primary.processUpdate(
                createUpdate(
                        "Bridge-02",
                        "STATUS",
                        "BLOCKED",
                        "Edge-01"
                )
        );
        System.out.println();
        System.out.println(
                "STEP 5: BACKUP FAILOVER"
        );
        backup.promoteToPrimary();

        System.out.println();
        System.out.println(
                "STEP 6: SYSTEM CONTINUES AFTER FAILURE"
        );
        System.out.println(
                "--------------------------------------------"
        );
        backup.processNewUpdate(
                createUpdate(
                        "School-07",
                        "STATUS",
                        "NEEDS_RESCUE",
                        "UAV-03"
                )
        );
        backup.processNewUpdate(
                createUpdate(
                        "Bridge-02",
                        "STATUS",
                        "BLOCKED",
                        "Edge-01"
                )
        );
        backup.processNewUpdate(
                createUpdate(
                        "House-20",
                        "STATUS",
                        "RESCUED",
                        "UAV-04"
                )
        );

        System.out.println();
        System.out.println(
                "STEP 7: FINAL SYSTEM STATE"
        );
        System.out.println(
                "Primary Status: "
                        + (primary.isActive()
                        ? "ACTIVE"
                        : "FAILED")
        );

        System.out.println(
                "Backup Status : "
                        + (backup.isPromoted()
                        ? "ACTIVE PRIMARY"
                        : "STANDBY")
        );


        backup.showState();
        System.out.println();
        System.out.println(
                "FAULT TOLERANCE TEST COMPLETED"
        );
        System.out.println(
                "Primary failure detected."
        );
        System.out.println(
                "Backup successfully took over."
        );
        System.out.println(
                "Previously replicated updates were preserved."
        );
        System.out.println(
                "New system updates were processed after failover."
        );
    }
    private static SystemUpdate createUpdate(
            String resourceId,
            String updateType,
            String value,
            String sourceNode) {
        updateCounter++;
        String updateId =
                "UPDATE-" + updateCounter;
        return new SystemUpdate(
                updateId,
                resourceId,
                updateType,
                value,
                sourceNode
        );
    }
}