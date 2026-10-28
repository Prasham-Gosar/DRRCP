package com.drs.consensus;

/**
 * Interface to query network partition state.
 * Expected from Prasham's com.drs.core package.
 */
public interface NetworkPartitionStatus {

    /** Whether the network is partitioned into isolated clusters. */
    boolean isPartitioned();

    /** Whether two clusters can communicate given current partition state. */
    boolean canCommunicate(String clusterA, String clusterB);
}

