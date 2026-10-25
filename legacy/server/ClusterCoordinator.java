package server;


import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Single central Cluster Coordinator process (Exp 1 + 2 + 3, server side).
 * Run this FIRST, in its own terminal, before the client.
 */
public class ClusterCoordinator {
    public static void main(String[] args) {
        try {
            ClusterCoordinatorImpl coordinator = new ClusterCoordinatorImpl();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("Coordinator", coordinator);

            System.out.println("=================================================");
            System.out.println("  Cluster Coordinator ready on port 1099 as 'Coordinator'");
            System.out.println("  Worker pool: 4 threads | Monitor: active (threshold=6000ms)");
            System.out.println("=================================================");

            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "MonitorThread");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(() -> coordinator.checkForSilentNodes(6000), 1, 1, TimeUnit.SECONDS);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
