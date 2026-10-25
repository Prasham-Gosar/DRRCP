package com.drs.core.event;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persisted event log entry. Every call to EventPublisher.publish() creates one of these
 * AND pushes the same payload live to all SSE subscribers.
 */
@Entity
@Table(name = "event_log")
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which experiment/module produced this event (e.g. "coordinator", "election", "heartbeat"). */
    @Column(nullable = false)
    private String module;

    /** Event type within that module (e.g. "NODE_REGISTERED", "HEARTBEAT_RECEIVED"). */
    @Column(name = "event_type", nullable = false)
    private String type;

    /** JSON-serialised payload. */
    @Column(columnDefinition = "CLOB")
    private String payload;

    @Column(name = "event_time", nullable = false)
    private Instant timestamp;

    protected EventLog() { }

    public EventLog(String module, String type, String payload) {
        this.module = module;
        this.type = type;
        this.payload = payload;
        this.timestamp = Instant.now();
    }

    // ── getters ──

    public Long getId()         { return id; }
    public String getModule()   { return module; }
    public String getType()     { return type; }
    public String getPayload()  { return payload; }
    public Instant getTimestamp(){ return timestamp; }
}
