package com.drs.consensus;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default stub for {@link NetworkPartitionStatus} until Prasham's partition controller is wired.
 */
@Component
@ConditionalOnMissingBean(name = "networkPartitionStatus")
public class DefaultNetworkPartitionStatus implements NetworkPartitionStatus {

    private volatile boolean partitioned = true;

    public void setPartitioned(boolean partitioned) {
        this.partitioned = partitioned;
    }

    @Override
    public boolean isPartitioned() {
        return partitioned;
    }

    @Override
    public boolean canCommunicate(String clusterA, String clusterB) {
        if (!partitioned) return true;
        if (clusterA == null || clusterB == null) return true;
        return clusterA.equalsIgnoreCase(clusterB);
    }
}

