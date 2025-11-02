package graph.dagsp;

import org.example.graph.common.WeightedDirectedGraph;
import org.example.graph.common.SimpleMetrics;
import org.example.graph.dagsp.DagShortestPaths;
import org.example.graph.scc.Condensation;
import org.example.graph.scc.TarjanSCC;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DagShortestPathsTest {
    @Test
    public void testShortestPathsLinear() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(4);
        g.addEdge(0,1,1); g.addEdge(1,2,2); g.addEdge(2,3,3);
        var res = DagShortestPaths.shortestPaths(g, 0, new SimpleMetrics());
        assertArrayEquals(new double[]{0.0,1.0,3.0,6.0}, res.dist, 1e-9);
        assertArrayEquals(new int[]{-1,0,1,2}, res.prev);
        assertArrayEquals(new int[]{0,1,2,3}, res.reconstructPath(3));
    }

    @Test
    public void testShortestPathsBranches() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(5);
        g.addEdge(0,1,5); g.addEdge(0,2,1); g.addEdge(2,3,1); g.addEdge(1,3,1); g.addEdge(3,4,1);
        var res = DagShortestPaths.shortestPaths(g, 0, new SimpleMetrics());
        assertEquals(2.0, res.dist[3], 1e-9); // 0->2->3 is shortest (1+1)
        int[] path = res.reconstructPath(4);
        assertArrayEquals(new int[]{0,2,3,4}, path);
    }

    @Test
    public void testLongestPaths() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(5);
        g.addEdge(0,1,2); g.addEdge(0,2,1); g.addEdge(1,3,4); g.addEdge(2,3,10); g.addEdge(3,4,3);
        var res = DagShortestPaths.longestPaths(g, 0, new SimpleMetrics());
        assertEquals(14.0, res.dist[4], 1e-9);
        assertArrayEquals(new int[]{0,2,3,4}, res.reconstructPath(4));
    }

    @Test
    public void testDisconnected() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(4);
        g.addEdge(0,1,1); // node 2,3 disconnected
        var res = DagShortestPaths.shortestPaths(g, 0, new SimpleMetrics());
        assertTrue(Double.isInfinite(res.dist[2]));
        assertTrue(Double.isInfinite(res.dist[3]));
        assertArrayEquals(new int[0], res.reconstructPath(2));
    }
    private static class CondensedFixture {
        final WeightedDirectedGraph dag;
        final int sourceComp;
        CondensedFixture(WeightedDirectedGraph dag, int sourceComp) {
            this.dag = dag; this.sourceComp = sourceComp;
        }
    }

    /**
     * Builds the example graph with a cycle {1,2,3} and chain 4->5->6 and an isolated 0.
     * Edges (edge-weight model):
     * 0
     * 0->1 (3), 1->2 (2), 2->3 (4), 3->1 (1)  // forms SCC {1,2,3}
     * 4->5 (2), 5->6 (5)
     * After SCC: SCC({1,2,3}) separate, and chain {4}->{5}->{6}.
     * Source is node 4, so source component is comp(4).
     */
    private CondensedFixture buildCondensedExample() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(7);
        g.addEdge(0, 1, 3);
        g.addEdge(1, 2, 2);
        g.addEdge(2, 3, 4);
        g.addEdge(3, 1, 1);
        g.addEdge(4, 5, 2);
        g.addEdge(5, 6, 5);
        var metrics = new SimpleMetrics();
        var scc = new TarjanSCC(g, metrics).run();
        var cond = Condensation.build(g, scc);
        int sourceComp = scc.comp()[4];
        return new CondensedFixture(cond.dag, sourceComp);
    }

    @Test
    public void testShortestPathFromSource() {
        var fx = buildCondensedExample();
        var metrics = new SimpleMetrics();
        var sp = DagShortestPaths.shortestPaths(fx.dag, fx.sourceComp, metrics);
         int finite = 0; double dmax = -1;
        for (double d : sp.dist) { if (!Double.isInfinite(d)) { finite++; dmax = Math.max(dmax, d); } }
        assertEquals(3, finite); // {comp(4), comp(5), comp(6)}
        assertEquals(7.0, dmax, 1e-9);
        int m = 0; for (int u = 0; u < fx.dag.n(); u++) m += fx.dag.adj(u).size();
        assertTrue(metrics.get("dag.sp.relax") <= m);
        assertTrue(metrics.get("dag.sp:timeNs") >= 0);
    }

    @Test
    public void testShortestPathReconstruction() {
        var fx = buildCondensedExample();
        var sp = DagShortestPaths.shortestPaths(fx.dag, fx.sourceComp, new SimpleMetrics());
        int target = -1; double best = -1;
        for (int i = 0; i < sp.dist.length; i++) if (!Double.isInfinite(sp.dist[i]) && sp.dist[i] > best) { best = sp.dist[i]; target = i; }
        int[] path = sp.reconstructPath(target);
        assertTrue(path.length >= 1);
        assertEquals(0.0, sp.dist[path[0]], 1e-9); // first is source comp
        assertEquals(7.0, sp.dist[target], 1e-9);
        for (int i = 1; i < path.length; i++) assertTrue(sp.dist[path[i]] > sp.dist[path[i-1]]);
    }

    @Test
     public void testLongestPath() {
        var fx = buildCondensedExample();
        var lp = DagShortestPaths.longestPaths(fx.dag, fx.sourceComp, new SimpleMetrics());
         double dmax = -1; int target = -1;
        for (int i = 0; i < lp.dist.length; i++) if (!Double.isInfinite(-lp.dist[i]) && lp.dist[i] > dmax) { dmax = lp.dist[i]; target = i; }
        assertEquals(7.0, dmax, 1e-9);
        int[] path = lp.reconstructPath(target);
        assertTrue(path.length >= 1);
        for (int i = 1; i < path.length; i++) assertTrue(lp.dist[path[i]] > lp.dist[path[i-1]]);
    }

    @Test
    @DisplayName("DAG-LP vs Shortest on negated weights")
    public void testLongestPathAlternative() {
        var fx = buildCondensedExample();
        var lp = DagShortestPaths.longestPaths(fx.dag, fx.sourceComp, new SimpleMetrics());
        WeightedDirectedGraph neg = new WeightedDirectedGraph(fx.dag.n());
        for (int u = 0; u < fx.dag.n(); u++) {
            for (var e : fx.dag.adj(u)) neg.addEdge(e.from, e.to, -e.weight);
        }
        var spNeg = DagShortestPaths.shortestPaths(neg, fx.sourceComp, new SimpleMetrics());
        double bestLP = Double.NEGATIVE_INFINITY; int t1 = -1;
        for (int i = 0; i < lp.dist.length; i++) if (!Double.isInfinite(-lp.dist[i]) && lp.dist[i] > bestLP) { bestLP = lp.dist[i]; t1 = i; }
        double bestSPNeg = Double.POSITIVE_INFINITY;
        for (int i = 0; i < spNeg.dist.length; i++) if (!Double.isInfinite(spNeg.dist[i]) && spNeg.dist[i] < bestSPNeg) { bestSPNeg = spNeg.dist[i]; }
        assertEquals(bestLP, -bestSPNeg, 1e-9);
    }

    @Test
    public void testZeroWeight() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(3);
        g.addEdge(0,1,0.0);
        g.addEdge(1,2,5.0);
        var sp = DagShortestPaths.shortestPaths(g, 0, new SimpleMetrics());
        assertEquals(0.0, sp.dist[1], 1e-9);
        assertEquals(5.0, sp.dist[2], 1e-9);
    }

    @Test
    public void testDisconnectedComponent() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(3);
        g.addEdge(0,1,1.0); // node 2 disconnected
        var sp = DagShortestPaths.shortestPaths(g, 0, new SimpleMetrics());
        assertTrue(Double.isInfinite(sp.dist[2]));
    }
}
