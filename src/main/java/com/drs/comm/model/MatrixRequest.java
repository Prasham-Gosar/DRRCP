package com.drs.comm.model;

/**
 * Request payload for Parallel Matrix Multiplication (Experiment 10).
 */
public class MatrixRequest {

    private int n = 400;
    private int threads = 4;
    private int maxThreads = 8;

    public MatrixRequest() {
    }

    public MatrixRequest(int n, int threads) {
        this.n = n;
        this.threads = threads;
    }

    public int getN() {
        return n;
    }

    public void setN(int n) {
        this.n = n;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    @Override
    public String toString() {
        return "MatrixRequest{" +
                "n=" + n +
                ", threads=" + threads +
                ", maxThreads=" + maxThreads +
                '}';
    }
}
