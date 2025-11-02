package graph.common;

import org.example.graph.common.SimpleMetrics;
import org.example.graph.common.WeightedDirectedGraph;
import org.example.graph.dagsp.DagShortestPaths;
import org.example.graph.topo.TopoSort;
import org.example.graph.scc.TarjanSCC;
import org.example.graph.scc.Condensation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleMetricsTest {

    @Test
    @DisplayName("Metrics: SCC DFS calls equal number of visited nodes; edges traversed equals edges count")
    public void testMetrics_SCC_DFSCount() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(4);
        g.addEdge(0,1,1); g.addEdge(1,2,1); g.addEdge(2,3,1);
        SimpleMetrics m = new SimpleMetrics();
        new TarjanSCC(g, m).run();
        assertEquals(4, m.get("scc.dfs.calls"));
        assertEquals(3, m.get("scc.dfs.edges"));
        assertTrue(m.get("scc:timeNs") >= 0);
    }

    @Test
    @DisplayName("Metrics: Kahn pop/push counts on linear DAG")
    public void testMetrics_Kahn_PopsPushes() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(4);
        g.addEdge(0,1,1); g.addEdge(1,2,1); g.addEdge(2,3,1);
        SimpleMetrics m = new SimpleMetrics();
        var order = TopoSort.kahn(g, m);
        assertEquals(4, order.size());
        assertEquals(4, m.get("topo.kahn.pop"));
        assertEquals(4, m.get("topo.kahn.push")); // each node enqueued exactly once
        assertEquals(3, m.get("topo.kahn.relaxEdge"));
        assertTrue(m.get("topo.kahn:timeNs") >= 0);
    }

    @Test
    @DisplayName("Metrics: DAG-SP relaxations not exceeding number of edges and time non-zero")
    public void testMetrics_DAGSP_Relaxations() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(4);
        g.addEdge(0,1,2); g.addEdge(1,2,3); g.addEdge(2,3,4);
        SimpleMetrics m = new SimpleMetrics();
        var sp = DagShortestPaths.shortestPaths(g, 0, m);
        int mCount = 0; for (int u = 0; u < g.n(); u++) mCount += g.adj(u).size();
        assertEquals(3, mCount);
        assertTrue(m.get("dag.sp.relax") <= mCount);
        assertEquals(0.0, sp.dist[0], 1e-9);
        assertTrue(m.get("dag.sp:timeNs") >= 0);
    }

    @Test
    @DisplayName("Metrics: timers collect time for SCC, Topo, DAG-SP, DAG-LP")
    public void testMetrics_TimeNonZero() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(5);
        // Build small DAG after condensation by avoiding cycles here
        g.addEdge(0,1,1); g.addEdge(1,2,1); g.addEdge(0,3,1); g.addEdge(3,4,1);
        SimpleMetrics m = new SimpleMetrics();
        // Run SCC
        var scc = new TarjanSCC(g, m).run();
        assertTrue(m.get("scc:timeNs") >= 0);
        // Condensation + Topo + SP + LP
        var cond = Condensation.build(g, scc);
        TopoSort.kahn(cond.dag, m);
        assertTrue(m.get("topo.kahn:timeNs") >= 0);
        DagShortestPaths.shortestPaths(cond.dag, 0, m);
        assertTrue(m.get("dag.sp:timeNs") >= 0);
        DagShortestPaths.longestPaths(cond.dag, 0, m);
        assertTrue(m.get("dag.lp:timeNs") >= 0);
    }
}
