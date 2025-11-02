package org.example.graph.scc;

import org.example.graph.common.Edge;
import org.example.graph.common.Graph;
import org.example.graph.common.WeightedDirectedGraph;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds condensation DAG given a graph and its SCC mapping.
 */
public class Condensation {
    public static class Result {
        public final WeightedDirectedGraph dag;
        public final int componentsCount;
        public final List<List<Integer>> components;
        public Result(WeightedDirectedGraph dag, int componentsCount, List<List<Integer>> components) {
            this.dag = dag;
            this.componentsCount = componentsCount;
            this.components = components;
        }
    }

    public static Result build(Graph g, TarjanSCC.Result scc) {
        int[] comp = scc.comp();
        List<List<Integer>> components = scc.components();
        int c = components.size();
        WeightedDirectedGraph dag = new WeightedDirectedGraph(c);
         Set<Long> seen = new HashSet<>();
        for (int u = 0; u < g.n(); u++) {
            int cu = comp[u];
            for (Edge e : g.adj(u)) {
                int cv = comp[e.to];
                if (cu != cv) {
                    long key = (((long) cu) << 32) | (cv & 0xffffffffL);
                    if (seen.add(key)) {
                        dag.addEdge(cu, cv, e.weight);
                    }
                }
            }
        }
        return new Result(dag, c, components);
    }
}
