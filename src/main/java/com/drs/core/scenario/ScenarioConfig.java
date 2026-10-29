package com.drs.core.scenario;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration options for running the full Disaster Relief scenario.
 * Sensible defaults are taken directly from the legacy {@code DisasterReliefClient}.
 */
public class ScenarioConfig {

    /** Delay in milliseconds simulating the partition duration (default 2000 ms). */
    private int partitionDurationMs = 2000;

    /** Injected physical clock drifts in milliseconds. */
    private Map<String, Long> skews = new LinkedHashMap<>();

    /** Fast execution mode for unit tests (0 ms delays). */
    private boolean fastMode = false;

    public ScenarioConfig() {
        // Defaults matching legacy client
        skews.put("UAV-01", 1500L);
        skews.put("UAV-02", -800L);
        skews.put("Edge-01", 300L);
    }

    public int getPartitionDurationMs() {
        return fastMode ? 0 : partitionDurationMs;
    }

    public void setPartitionDurationMs(int partitionDurationMs) {
        this.partitionDurationMs = partitionDurationMs;
    }

    public Map<String, Long> getSkews() {
        return skews;
    }

    public void setSkews(Map<String, Long> skews) {
        if (skews != null) {
            this.skews = new LinkedHashMap<>(skews);
        }
    }

    public void setClockSyncDriftMs(int ms) {
        skews.put("UAV-01", (long) ms);
    }

    public long getSkew(String nodeId, long defaultSkew) {
        return skews.getOrDefault(nodeId, defaultSkew);
    }

    public boolean isFastMode() {
        return fastMode;
    }

    public void setFastMode(boolean fastMode) {
        this.fastMode = fastMode;
    }
}
