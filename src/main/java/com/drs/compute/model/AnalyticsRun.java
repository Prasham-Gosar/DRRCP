package com.drs.compute.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * JPA entity persisting historical executions of MapReduce analytics runs (Exp 7).
 */
@Entity
@Table(name = "analytics_runs")
public class AnalyticsRun implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant timestamp;

    private String engine;          // "spark", "streams", or "streams-fallback"

    private long durationMs;

    private int totalTasks;

    private double totalWorkload;

    private String status;          // "SUCCESS", "FAILED"

    @Lob
    @Column(name = "result_json", columnDefinition = "CLOB")
    private String resultJson;

    protected AnalyticsRun() {
    }

    public AnalyticsRun(String engine, long durationMs, int totalTasks, double totalWorkload,
                        String status, String resultJson) {
        this.timestamp = Instant.now();
        this.engine = engine;
        this.durationMs = durationMs;
        this.totalTasks = totalTasks;
        this.totalWorkload = totalWorkload;
        this.status = status;
        this.resultJson = resultJson;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getTotalTasks() {
        return totalTasks;
    }

    public void setTotalTasks(int totalTasks) {
        this.totalTasks = totalTasks;
    }

    public double getTotalWorkload() {
        return totalWorkload;
    }

    public void setTotalWorkload(double totalWorkload) {
        this.totalWorkload = totalWorkload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }
}

