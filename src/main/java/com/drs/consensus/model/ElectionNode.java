package com.drs.consensus.model;

/**
 * A node participating in a leader election (Bully or Ring).
 * <p>
 * Higher {@code priority} wins. {@code alive} indicates whether the node is reachable.
 * This is a direct port of {@code common.ElectionNode} from the legacy console code.
 */
public class ElectionNode {

    private final String id;
    private final int priority;
    private boolean alive;

    public ElectionNode(String id, int priority, boolean alive) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
        this.priority = priority;
        this.alive = alive;
    }

    public String getId() { return id; }
    public int getPriority() { return priority; }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }

    @Override
    public String toString() {
        return id + "(p=" + priority + ", " + (alive ? "ALIVE" : "DEAD") + ")";
    }
}
