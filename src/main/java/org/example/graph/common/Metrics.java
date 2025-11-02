package org.example.graph.common;

/**
 * Metrics interface to collect algorithm instrumentation and timings.
 */
public interface Metrics {
    void inc(String key);
    void add(String key, long delta);
    long get(String key);
    void startTimer(String key);
    void stopTimer(String key);
}
