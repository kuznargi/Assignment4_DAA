package org.example;

import org.example.graph.common.JsonGraphLoader;
import org.example.graph.common.SimpleMetrics;
import org.example.graph.common.WeightedDirectedGraph;
import org.example.graph.dagsp.DagShortestPaths;
import org.example.graph.scc.Condensation;
import org.example.graph.scc.TarjanSCC;
import org.example.graph.topo.TopoSort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrationPipelineTest {

    @Test
    public void testFullPipelineOnProvidedDataset() throws Exception {
        File f = new File("data/tasks.json");
        assertTrue(f.exists(), "data/tasks.json must exist");
        var loaded = JsonGraphLoader.load(f);
        WeightedDirectedGraph g = loaded.graph;
        var metrics = new SimpleMetrics();

        var scc = new TarjanSCC(g, metrics).run();
        // Expect SCC {1,2,3}, and singletons {0}, {4}, {5}, {6}, {7} => total 6 components
        assertEquals(6, scc.components().size());

        var cond = Condensation.build(g, scc);
        // Topo sort must have size == components
        List<Integer> topo = TopoSort.kahn(cond.dag, metrics);
        assertEquals(cond.componentsCount, topo.size());

        // Source is 4 -> find its component
        int sourceComp = scc.comp()[4];
        var sp = DagShortestPaths.shortestPaths(cond.dag, sourceComp, metrics);

        int c4 = scc.comp()[4];
        int c5 = scc.comp()[5];
        int c6 = scc.comp()[6];
        int c7 = scc.comp()[7];

        assertEquals(0.0, sp.dist[c4], 1e-9);
        assertEquals(2.0, sp.dist[c5], 1e-9);
        assertEquals(7.0, sp.dist[c6], 1e-9);
        assertEquals(8.0, sp.dist[c7], 1e-9);

        int c123 = scc.comp()[1];
        int c0 = scc.comp()[0];
        assertTrue(Double.isInfinite(sp.dist[c123]));
        assertTrue(Double.isInfinite(sp.dist[c0]));
    }
    @Test
    @DisplayName("Full pipeline: small linear DAG")
    public void testFullPipeline_SmallGraph() {
        // 0->1->2->3
        WeightedDirectedGraph g = new WeightedDirectedGraph(4);
        g.addEdge(0,1,1); g.addEdge(1,2,2); g.addEdge(2,3,3);
        var metrics = new SimpleMetrics();
        var scc = new TarjanSCC(g, metrics).run();
        var cond = Condensation.build(g, scc);
        List<Integer> topo = TopoSort.kahn(cond.dag, metrics);
        assertEquals(cond.componentsCount, topo.size());
        int sourceComp = scc.comp()[0];
        var sp = DagShortestPaths.shortestPaths(cond.dag, sourceComp, metrics);
        var lp = DagShortestPaths.longestPaths(cond.dag, sourceComp, metrics);
        // shortest distances should be 0,1,3,6
        assertEquals(0.0, sp.dist[scc.comp()[0]], 1e-9);
        assertEquals(1.0, sp.dist[scc.comp()[1]], 1e-9);
        assertEquals(3.0, sp.dist[scc.comp()[2]], 1e-9);
        assertEquals(6.0, sp.dist[scc.comp()[3]], 1e-9);
        // longest equals shortest here because only one path
        assertEquals(6.0, lp.dist[scc.comp()[3]], 1e-9);
    }

    @Test
    @DisplayName("Full pipeline: with a cycle compressed into one SCC")
    public void testFullPipeline_WithCycle() {
        // (0<->1) -> 2 -> 3
        WeightedDirectedGraph g = new WeightedDirectedGraph(4);
        g.addEdge(0,1,1); g.addEdge(1,0,1);
        g.addEdge(1,2,2); g.addEdge(2,3,3);
        var metrics = new SimpleMetrics();
        var scc = new TarjanSCC(g, metrics).run();
        // expect at least 3 components: {0,1}, {2}, {3}
        assertTrue(scc.components().size() >= 3);
        var cond = Condensation.build(g, scc);
        var topo = TopoSort.kahn(cond.dag, metrics);
        assertEquals(cond.componentsCount, topo.size());
        int sourceComp = scc.comp()[0];
        var sp = DagShortestPaths.shortestPaths(cond.dag, sourceComp, metrics);
        // distances must be finite along the chain of components
        for (int v : topo) {
            assertFalse(Double.isNaN(sp.dist[v]));
        }
    }

    @Test
    @DisplayName("Full pipeline: multiple SCCs with dependencies")
    public void testFullPipeline_MultipleSCCs() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(5);
        g.addEdge(0,1,1); g.addEdge(1,0,1);
        g.addEdge(2,3,1); g.addEdge(3,2,1);
        g.addEdge(1,2,5);
        g.addEdge(3,4,2);
        var metrics = new SimpleMetrics();
        var scc = new TarjanSCC(g, metrics).run();
        var cond = Condensation.build(g, scc);
        var order = TopoSort.kahn(cond.dag, metrics);
        assertEquals(cond.componentsCount, order.size());
        int compA = scc.comp()[0];
        int compB = scc.comp()[2];
        int comp4 = scc.comp()[4];
        assertTrue(order.indexOf(compA) < order.indexOf(compB));
        assertTrue(order.indexOf(compB) < order.indexOf(comp4));
         var sp = DagShortestPaths.shortestPaths(cond.dag, compA, metrics);
        assertFalse(Double.isInfinite(sp.dist[compB]));
        assertFalse(Double.isInfinite(sp.dist[comp4]));
    }
}
