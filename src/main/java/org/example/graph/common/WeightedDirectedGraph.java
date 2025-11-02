package org.example.graph.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple adjacency-list based directed weighted graph.
 */
public class WeightedDirectedGraph implements Graph {
    private final int n;
    private final List<List<Edge>> adj;

    public WeightedDirectedGraph(int n) {
        if (n < 0) throw new IllegalArgumentException("n must be >= 0");
        this.n = n;
        this.adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
    }

    @Override
    public int n() {
        return n;
    }

    @Override
    public List<Edge> adj(int u) {
        return Collections.unmodifiableList(adj.get(u));
    }

    @Override
    public void addEdge(int u, int v, double w) {
        if (u < 0 || u >= n || v < 0 || v >= n) {
            throw new IndexOutOfBoundsException("u or v out of range");
        }
        adj.get(u).add(new Edge(u, v, w));
    }

    public static WeightedDirectedGraph fromEdges(int n, List<Edge> edges) {
        WeightedDirectedGraph g = new WeightedDirectedGraph(n);
        for (Edge e : edges) g.addEdge(e.from, e.to, e.weight);
        return g;
    }
}
