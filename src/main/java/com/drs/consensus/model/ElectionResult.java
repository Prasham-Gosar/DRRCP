package com.drs.consensus.model;

import java.util.List;

/**
 * Result of running a leader election (Bully or Ring).
 * <p>
 * {@code steps} replaces all the {@code System.out.println} calls from the legacy code,
 * giving the UI a list of messages it can animate step by step.
 */
public class ElectionResult {

    private final String algorithm;
    private final String leaderId;
    private final int leaderPriority;
    private final int totalMessages;
    private final List<String> steps;

    public ElectionResult(String algorithm, String leaderId, int leaderPriority,
                          int totalMessages, List<String> steps) {
        this.algorithm = algorithm;
        this.leaderId = leaderId;
        this.leaderPriority = leaderPriority;
        this.totalMessages = totalMessages;
        this.steps = steps;
    }

    public String getAlgorithm()    { return algorithm; }
    public String getLeaderId()     { return leaderId; }
    public int getLeaderPriority()  { return leaderPriority; }
    public int getTotalMessages()   { return totalMessages; }
    public List<String> getSteps()  { return steps; }

    @Override
    public String toString() {
        return algorithm + " → leader=" + leaderId + " (p=" + leaderPriority
                + "), msgs=" + totalMessages;
    }
}
