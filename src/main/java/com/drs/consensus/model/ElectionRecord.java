package com.drs.consensus.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persisted record of an election run (Exp 4).
 * <p>
 * One row per algorithm execution (Bully or Ring). History is exposed
 * via {@code GET /api/election/history} for the UI's history table.
 */
@Entity
@Table(name = "election_record")
public class ElectionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String algorithm;

    @Column(nullable = false)
    private String leaderId;

    private int leaderPriority;

    private int totalMessages;

    @Column(nullable = false)
    private String clusterId;

    @Column(name = "election_time", nullable = false)
    private Instant timestamp;

    protected ElectionRecord() { }

    public ElectionRecord(String algorithm, String leaderId, int leaderPriority,
                          int totalMessages, String clusterId) {
        this.algorithm = algorithm;
        this.leaderId = leaderId;
        this.leaderPriority = leaderPriority;
        this.totalMessages = totalMessages;
        this.clusterId = clusterId;
        this.timestamp = Instant.now();
    }

    // ── getters ──

    public Long getId()            { return id; }
    public String getAlgorithm()   { return algorithm; }
    public String getLeaderId()    { return leaderId; }
    public int getLeaderPriority() { return leaderPriority; }
    public int getTotalMessages()  { return totalMessages; }
    public String getClusterId()   { return clusterId; }
    public Instant getTimestamp()   { return timestamp; }
}
