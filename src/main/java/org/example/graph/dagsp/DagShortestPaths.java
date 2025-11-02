package org.example.graph.dagsp;

import org.example.graph.common.Edge;
import org.example.graph.common.WeightedDirectedGraph;
import org.example.graph.common.Metrics;
import org.example.graph.topo.TopoSort;

import java.util.Arrays;
import java.util.List;

/**
 * Single-source shortest and longest paths on a DAG using topological order.
 * Supports edge-weight model.
 */
public class DagShortestPaths {

    public static class Result {
        public final double[] dist;
        public final int[] prev;
        public final int source;
        public Result(double[] dist, int[] prev, int source) {
            this.dist = dist;
            this.prev = prev;
            this.source = source;
        }
        public int[] reconstructPath(int target) {
            if (Double.isInfinite(dist[target])) return new int[0];
            int cnt = 0; int v = target;
            while (v != -1) { cnt++; v = prev[v]; }
            int[] path = new int[cnt];
            v = target;
            for (int i = cnt-1; i >= 0; i--) { path[i] = v; v = prev[v]; }
            return path;
        }
    }

    public static Result shortestPaths(WeightedDirectedGraph g, int source, Metrics metrics) {
        int n = g.n();
        double[] dist = new double[n];
        int[] prev = new int[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);
        dist[source] = 0.0;
        List<Integer> order = TopoSort.kahn(g, metrics);
        metrics.startTimer("dag.sp");
        for (int u : order) {
            if (Double.isInfinite(dist[u])) continue; // unreachable
            for (Edge e : g.adj(u)) {
                metrics.inc("dag.sp.relax");
                double nd = dist[u] + e.weight;
                if (nd < dist[e.to]) {
                    dist[e.to] = nd;
                    prev[e.to] = u;
                }
            }
        }
        metrics.stopTimer("dag.sp");
        return new Result(dist, prev, source);
    }

    public static Result longestPaths(WeightedDirectedGraph g, int source, Metrics metrics) {
        int n = g.n();
        double[] dist = new double[n];
        int[] prev = new int[n];
        Arrays.fill(dist, Double.NEGATIVE_INFINITY);
        Arrays.fill(prev, -1);
        dist[source] = 0.0;
        List<Integer> order = TopoSort.kahn(g, metrics);
        metrics.startTimer("dag.lp");
        for (int u : order) {
            if (Double.isInfinite(-dist[u])) continue; // treat -inf as unreachable
            for (Edge e : g.adj(u)) {
                metrics.inc("dag.lp.relax");
                double nd = dist[u] + e.weight;
                if (nd > dist[e.to]) {
                    dist[e.to] = nd;
                    prev[e.to] = u;
                }
            }
        }
        metrics.stopTimer("dag.lp");
        return new Result(dist, prev, source);
    }
}
