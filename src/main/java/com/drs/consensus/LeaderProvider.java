package com.drs.consensus;

/**
 * Interface exposed by the consensus module for other modules (e.g.
 * compute/load balancer)
 * to query the current elected leader for a given cluster.
 */
public interface LeaderProvider {

    /**
     * Returns the node ID of the current leader for the specified cluster,
     * or null if no election has completed yet.
     *
     * @param clusterId cluster identifier (e.g. "Cluster-A")
     * @return elected leader node ID or null
     */
    String currentLeader(String clusterId);
}
