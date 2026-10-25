package common;

public class ElectionNode {
    public final String id;
    public final int priority;
    public boolean alive;

    public ElectionNode(String id, int priority, boolean alive) {
        this.id = id;
        this.priority = priority;
        this.alive = alive;
    }
}
