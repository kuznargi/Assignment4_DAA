package org.example.graph.topo;

import org.example.graph.common.Edge;
import org.example.graph.common.WeightedDirectedGraph;
import org.example.graph.common.Metrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Kahn's algorithm for topological sorting.
 */
public class TopoSort {
    public static List<Integer> kahn(WeightedDirectedGraph g, Metrics metrics) {
        int n = g.n();
        int[] indeg = new int[n];
        for (int u = 0; u < n; u++) {
            for (Edge e : g.adj(u)) indeg[e.to]++;
        }
        Deque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < n; i++) if (indeg[i] == 0) { q.add(i); metrics.inc("topo.kahn.push"); }
        List<Integer> order = new ArrayList<>(n);
        metrics.startTimer("topo.kahn");
        while (!q.isEmpty()) {
            int u = q.removeFirst();
            metrics.inc("topo.kahn.pop");
            order.add(u);
            for (Edge e : g.adj(u)) {
                metrics.inc("topo.kahn.relaxEdge");
                if (--indeg[e.to] == 0) {
                    q.addLast(e.to);
                    metrics.inc("topo.kahn.push");
                }
            }
        }
        metrics.stopTimer("topo.kahn");
        if (order.size() != n) throw new IllegalStateException("Graph is not a DAG");
        return order;
    }
}
