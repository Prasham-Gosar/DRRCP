package com.drs.core.service;

import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;

/**
 * Exp 2: a named fixed thread pool (4 threads) for processing heartbeats.
 * Thread names: heartbeat-pool-1 ... heartbeat-pool-4.
 */
@Component
public class HeartbeatPool {

    private static final int POOL_SIZE = 4;

    private final ExecutorService pool;

    public HeartbeatPool() {
        this.pool = Executors.newFixedThreadPool(POOL_SIZE, new ThreadFactory() {
            private int counter = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "heartbeat-pool-" + (++counter));
                t.setDaemon(true);
                return t;
            }
        });
    }

    public <T> T submit(Callable<T> task) {
        Future<T> future = pool.submit(task);
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("Heartbeat pool task timed out", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Heartbeat pool task failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Heartbeat pool task interrupted", e);
        }
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}