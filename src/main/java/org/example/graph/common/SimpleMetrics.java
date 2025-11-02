package org.example.graph.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-unsafe simple metrics collector using String counters and timers.
 */
public class SimpleMetrics implements Metrics {
    private final Map<String, Long> counters = new HashMap<>();
    private final Map<String, Long> timers = new HashMap<>();

    @Override
    public void inc(String key) {
        counters.merge(key, 1L, Long::sum);
    }

    @Override
    public void add(String key, long delta) {
        counters.merge(key, delta, Long::sum);
    }

    @Override
    public long get(String key) {
        return counters.getOrDefault(key, 0L);
    }

    @Override
    public void startTimer(String key) {
        timers.put(key, System.nanoTime());
    }

    @Override
    public void stopTimer(String key) {
        Long start = timers.remove(key);
        if (start != null) {
            long elapsed = System.nanoTime() - start;
            add(key + ":timeNs", elapsed);
        }
    }
}
