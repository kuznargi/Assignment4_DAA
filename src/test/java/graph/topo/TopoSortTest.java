package graph.topo;

import org.example.graph.common.WeightedDirectedGraph;
import org.example.graph.common.SimpleMetrics;
import org.example.graph.scc.Condensation;
import org.example.graph.scc.TarjanSCC;
import org.example.graph.topo.TopoSort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TopoSortTest {
    @Test
    public void testLinearDAG() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(4);
        g.addEdge(0,1,1); g.addEdge(1,2,1); g.addEdge(2,3,1);
        var order = TopoSort.kahn(g, new SimpleMetrics());
        assertEquals(List.of(0,1,2,3), order);
    }

    @Test
    public void testBranchingDAG() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(5);
        g.addEdge(0,1,1); g.addEdge(0,2,1); g.addEdge(1,3,1); g.addEdge(2,3,1); g.addEdge(3,4,1);
        var order = TopoSort.kahn(g, new SimpleMetrics());
        assertTrue(order.indexOf(0) < order.indexOf(1));
        assertTrue(order.indexOf(0) < order.indexOf(2));
        assertTrue(order.indexOf(1) < order.indexOf(3));
        assertTrue(order.indexOf(2) < order.indexOf(3));
        assertTrue(order.indexOf(3) < order.indexOf(4));
    }

    @Test
    public void testSingleNode() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(1);
        var order = TopoSort.kahn(g, new SimpleMetrics());
        assertEquals(List.of(0), order);
    }
    @Test
    public void testCondensationOrder() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(4);
        g.addEdge(0,1,1); g.addEdge(1,0,1); // SCC A
        g.addEdge(2,3,1); g.addEdge(3,2,1); // SCC B
        g.addEdge(1,2,1); // cross edge A -> B
        var scc = new TarjanSCC(g, new SimpleMetrics()).run();
        var cond = Condensation.build(g, scc);

        var metrics = new SimpleMetrics();
        List<Integer> order = TopoSort.kahn(cond.dag, metrics);
        assertEquals(cond.componentsCount, order.size());
        int compA = scc.comp()[0];
        int compB = scc.comp()[2];
        assertTrue(order.indexOf(compA) < order.indexOf(compB));
        assertEquals(cond.dag.n(), metrics.get("topo.kahn.pop"));
    }

    @Test
    public void testDerivedTaskOrder() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(3);
        g.addEdge(0,1,1); g.addEdge(1,0,1); // SCC1
        g.addEdge(1,2,1); // to SCC2
        var scc = new TarjanSCC(g, new SimpleMetrics()).run();
        var cond = Condensation.build(g, scc);
        var order = TopoSort.kahn(cond.dag, new SimpleMetrics());
        int compSCC1 = scc.comp()[0];
        int compSCC2 = scc.comp()[2];
        assertTrue(order.indexOf(compSCC1) < order.indexOf(compSCC2));
        assertTrue(compSCC1 == scc.comp()[0] && compSCC1 == scc.comp()[1]);
        assertTrue(compSCC2 == scc.comp()[2]);
    }

    @Test
    @DisplayName("Topo: один узел")
    public void testSingleNodeDAG() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(1);
        var metrics = new SimpleMetrics();
        var order = TopoSort.kahn(g, metrics);
        assertEquals(List.of(0), order);
        assertEquals(1, metrics.get("topo.kahn.pop"));
        assertEquals(1, metrics.get("topo.kahn.push"));
         assertEquals(0, metrics.get("topo.kahn.relaxEdge"));
    }

    @Test
    public void testKahnThrowsOnCycle() {
        WeightedDirectedGraph g = new WeightedDirectedGraph(2);
        g.addEdge(0,1,1);
        g.addEdge(1,0,1);
        assertThrows(IllegalStateException.class, () -> TopoSort.kahn(g, new SimpleMetrics()));
    }
}
