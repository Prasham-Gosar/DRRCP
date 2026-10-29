package com.drs.comm.model;

/**
 * Represents an individual event in the per-rank execution timeline.
 */
public class RankTimelineEntry {

    private int rank;
    private String action;
    private Object data;
    private int order;

    public RankTimelineEntry() {
    }

    public RankTimelineEntry(int rank, String action, Object data, int order) {
        this.rank = rank;
        this.action = action;
        this.data = data;
        this.order = order;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public String toString() {
        return "RankTimelineEntry{" +
                "rank=" + rank +
                ", action='" + action + '\'' +
                ", data=" + data +
                ", order=" + order +
                '}';
    }
}
