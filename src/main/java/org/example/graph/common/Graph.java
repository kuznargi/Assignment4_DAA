package org.example.graph.common;

import java.util.List;

/**
 * Directed weighted graph interface using adjacency lists.
 */
public interface Graph {
    int n();
    List<Edge> adj(int u);
    void addEdge(int u, int v, double w);
}
