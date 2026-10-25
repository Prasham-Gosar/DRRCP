package com.drs.core.model;

import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeStatusTest {

    @Test
    void scoreIsTwiceSignalPlusBattery() {
        NodeStatus n = new NodeStatus("UAV-01", 80, 70, "UAV", "Cluster-A");
        assertEquals(220, n.getConnectivityScore());   // 2*70 + 80
    }

    @Test
    void stateBandsMatchLegacyThresholds() {
        assertEquals("STABLE",           new NodeStatus("a", 50, 50, "UAV", "C").getState());  // 150
        assertEquals("AT_RISK",          new NodeStatus("b", 49, 50, "UAV", "C").getState());  // 149
        assertEquals("AT_RISK",          new NodeStatus("c", 50, 25, "UAV", "C").getState());  // 100
        assertEquals("PARTITION_LIKELY", new NodeStatus("d", 49, 25, "UAV", "C").getState());  // 99
        assertEquals("PARTITION_LIKELY", new NodeStatus("e", 50, 0,  "UAV", "C").getState());  // 50
        assertEquals("PARTITIONED",      new NodeStatus("f", 49, 0,  "UAV", "C").getState());  // 49
    }

    @Test
    void updatingBatteryOrSignalRecomputesScore() {
        NodeStatus n = new NodeStatus("UAV-01", 10, 10, "UAV", "Cluster-A");
        assertEquals("PARTITIONED", n.getState());
        n.setSignalStrength(90);
        n.setBatteryLevel(90);
        assertEquals(270, n.getConnectivityScore());
        assertEquals("STABLE", n.getState());
    }

    @Test
    void survivesJavaSerialization_neededForRmi() throws Exception {
        NodeStatus original = new NodeStatus("Edge-01", 90, 85, "EDGE", "Cluster-B");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            NodeStatus copy = (NodeStatus) in.readObject();
            assertEquals("Edge-01", copy.getNodeId());
            assertEquals(original.getConnectivityScore(), copy.getConnectivityScore());
            assertEquals("Cluster-B", copy.getClusterId());
        }
    }
}
