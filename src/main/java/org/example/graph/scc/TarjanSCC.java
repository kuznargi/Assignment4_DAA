package org.example.graph.scc;

import org.example.graph.common.Graph;
import org.example.graph.common.Metrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Tarjan's algorithm for strongly connected components in O(V+E).
 */
public class TarjanSCC {
    private final Graph g;
    private final Metrics metrics;

    private int time = 0;
    private final int[] disc;
    private final int[] low;
    private final boolean[] onStack;
    private final int[] comp;
    private int compCount = 0;
    private final Deque<Integer> stack = new ArrayDeque<>();

    public TarjanSCC(Graph g, Metrics metrics) {
        this.g = g;
        this.metrics = metrics;
        int n = g.n();
        disc = new int[n];
        low = new int[n];
        onStack = new boolean[n];
        comp = new int[n];
        for (int i = 0; i < n; i++) {
            disc[i] = -1;
            low[i] = -1;
            comp[i] = -1;
        }
    }

    public Result run() {
        metrics.startTimer("scc");
        for (int u = 0; u < g.n(); u++) {
            if (disc[u] == -1) dfs(u);
        }
        metrics.stopTimer("scc");
        List<List<Integer>> components = new ArrayList<>(compCount);
        for (int i = 0; i < compCount; i++) components.add(new ArrayList<>());
        for (int v = 0; v < g.n(); v++) components.get(comp[v]).add(v);
        return new Result(comp, components);
    }

    private void dfs(int u) {
        metrics.inc("scc.dfs.calls");
        disc[u] = low[u] = time++;
        stack.push(u);
        onStack[u] = true;
        g.adj(u).forEach(e -> {
            metrics.inc("scc.dfs.edges");
            int v = e.to;
            if (disc[v] == -1) {
                dfs(v);
                low[u] = Math.min(low[u], low[v]);
            } else if (onStack[v]) {
                low[u] = Math.min(low[u], disc[v]);
            }
        });
        if (low[u] == disc[u]) {
            while (true) {
                int v = stack.pop();
                onStack[v] = false;
                comp[v] = compCount;
                if (v == u) break;
            }
            compCount++;
        }
    }

    public record Result(int[] comp, List<List<Integer>> components) {}
}
