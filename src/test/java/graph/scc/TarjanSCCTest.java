package graph.scc;

import org.example.graph.common.WeightedDirectedGraph;
import org.example.graph.common.SimpleMetrics;
import org.example.graph.scc.Condensation;
import org.example.graph.scc.TarjanSCC;
import org.example.graph.topo.TopoSort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TarjanSCCTest {
    @Test
    public void testSingleCycle() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(3);
        g.addEdge(0,1,1); g.addEdge(1,2,1); g.addEdge(2,0,1);
        var metrics = new SimpleMetrics();
        var res = new TarjanSCC(g, metrics).run();
        assertEquals(1, res.components().size());
        assertEquals(List.of(0,1,2).size(), res.components().get(0).size());
    }

    @Test
    public void testNoCyclesLinear() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(4);
        g.addEdge(0,1,1); g.addEdge(1,2,1); g.addEdge(2,3,1);
        var res = new TarjanSCC(g, new SimpleMetrics()).run();
        assertEquals(4, res.components().size());
        for (int i = 0; i < 4; i++) {
            assertEquals(1, res.components().get(res.comp()[i]).size());
        }
    }

    @Test
    public void testMultipleSCCs() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(6);
        g.addEdge(0,1,1); g.addEdge(1,0,1);
        g.addEdge(1,2,1);
        g.addEdge(2,3,1); g.addEdge(3,2,1);
        g.addEdge(4,5,1);
        var res = new TarjanSCC(g, new SimpleMetrics()).run();
        assertTrue(res.components().size() >= 4);
    }

    @Test
    public void testEmptyGraph() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(0);
        var res = new TarjanSCC(g, new SimpleMetrics()).run();
        assertEquals(0, res.components().size());
    }

    @Test
    public void testSingleNodeSelfLoop() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(1);
        g.addEdge(0,0,1);
        var res = new TarjanSCC(g, new SimpleMetrics()).run();
        assertEquals(1, res.components().size());
        assertEquals(1, res.components().get(0).size());
        assertEquals(0, res.components().get(0).get(0));
    }

    @Test
    public void testTwoSeparateCycles() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(4);
        g.addEdge(0,1,1); g.addEdge(1,0,1);
        g.addEdge(2,3,1); g.addEdge(3,2,1);
        var metrics = new SimpleMetrics();
        TarjanSCC.Result res = new TarjanSCC(g, metrics).run();
        assertEquals(2, res.components().size());
        Set<Set<Integer>> setSccs = new HashSet<>();
        for (List<Integer> comp : res.components()) setSccs.add(new HashSet<>(comp));
        assertTrue(setSccs.contains(Set.of(0,1)));
        assertTrue(setSccs.contains(Set.of(2,3)));
        assertEquals(4, metrics.get("scc.dfs.calls"));
        assertEquals(4, metrics.get("scc.dfs.edges"));
    }

    @Test
    public void testSingleNodeNoEdges() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(1);
        var metrics = new SimpleMetrics();
        TarjanSCC.Result res = new TarjanSCC(g, metrics).run();
        assertEquals(1, res.components().size());
        assertEquals(List.of(0), res.components().get(0));
        assertEquals(1, metrics.get("scc.dfs.calls"));
        assertEquals(0, metrics.get("scc.dfs.edges"));
    }

    @Test
    public void testCondensationGraph() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(4);
        g.addEdge(0,1,1); g.addEdge(1,0,1); // SCC A = {0,1}
        g.addEdge(1,2,5); // edge from A to node 2 (SCC B)
        var metrics = new SimpleMetrics();
        var scc = new TarjanSCC(g, metrics).run();
        var cond = Condensation.build(g, scc);

        assertEquals(3, cond.componentsCount);
         var order = TopoSort.kahn(cond.dag, new SimpleMetrics());
        assertEquals(3, order.size());

        int comp01 = scc.comp()[0];
        int comp2 = scc.comp()[2];
        int m = 0; boolean found = false;
        for (int u = 0; u < cond.dag.n(); u++) {
            m += cond.dag.adj(u).size();
            for (var e : cond.dag.adj(u)) if (e.from == comp01 && e.to == comp2) found = true;
        }
        assertTrue(found, "Edge from SCC{0,1} to SCC{2} must exist");
        assertEquals(1, m);
    }
}
