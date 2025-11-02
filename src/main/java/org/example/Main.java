package org.example;

import org.example.utils.InputJsonGenerator;
import org.example.utils.InputReader;
import org.example.utils.Metrics;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.graph.common.JsonGraphLoader;
import org.example.graph.common.SimpleMetrics;
import org.example.graph.common.WeightedDirectedGraph;
import org.example.graph.dagsp.DagShortestPaths;
import org.example.graph.scc.Condensation;
import org.example.graph.scc.TarjanSCC;
import org.example.graph.topo.TopoSort;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the SCC → Condensation → Topo → DAG Shortest/Longest pipeline.
 * - Batch mode (no args): prints full assignment description, generates data if needed, processes all graphs, outputs JSON/CSV.
 * - Single mode (with arg): processes one graph from file, prints to console.
 */
public class Main {

    private record Stats(int vertices, int edges) {}

    private static Stats count(WeightedDirectedGraph g) {
        int m = 0;
        for (int u = 0; u < g.n(); u++) m += g.adj(u).size();
        return new Stats(g.n(), m);
    }

    private static ObjectNode runSingle(ObjectMapper mapper, InputReader.GraphData gd, StringBuilder csvRows) {
        WeightedDirectedGraph g = gd.getGraph();
        var sm = new SimpleMetrics();

        Stats s = count(g);

        var sccRes = new TarjanSCC(g, sm).run();
        long sccOps = sm.get("scc.dfs.calls") + sm.get("scc.dfs.edges");
        double sccTimeMs = sm.get("scc:timeNs") / 1_000_000.0;

        var cond = Condensation.build(g, sccRes);
        List<Integer> topoOrder = TopoSort.kahn(cond.dag, sm);
        long topoOps = sm.get("topo.kahn.push") + sm.get("topo.kahn.pop") + sm.get("topo.kahn.relaxEdge");
        double topoTimeMs = sm.get("topo.kahn:timeNs") / 1_000_000.0;

        int sourceComp = sccRes.comp()[gd.getSource()];
        var spRes = DagShortestPaths.shortestPaths(cond.dag, sourceComp, sm);
        long spOps = sm.get("dag.sp.relax");
        double spTimeMs = sm.get("dag.sp:timeNs") / 1_000_000.0;

        int bestT = sourceComp;
        double best = 0.0;
        for (int i = 0; i < spRes.dist.length; i++) {
            if (!Double.isInfinite(spRes.dist[i]) && spRes.dist[i] > best) { best = spRes.dist[i]; bestT = i; }
        }
        int[] spPath = spRes.reconstructPath(bestT);

        var lpRes = DagShortestPaths.longestPaths(cond.dag, sourceComp, sm);
        long lpOps = sm.get("dag.lp.relax");
        double lpTimeMs = sm.get("dag.lp:timeNs") / 1_000_000.0;

        int bestLT = sourceComp; double bestL = 0.0;
        for (int i = 0; i < lpRes.dist.length; i++) {
            if (!Double.isInfinite(-lpRes.dist[i]) && lpRes.dist[i] > bestL) { bestL = lpRes.dist[i]; bestLT = i; }
        }
        int[] lpPath = lpRes.reconstructPath(bestLT);

        ObjectNode result = mapper.createObjectNode();
        result.put("graph_id", gd.getId());
        ObjectNode inputStats = mapper.createObjectNode();
        inputStats.put("vertices", s.vertices());
        inputStats.put("edges", s.edges());
        inputStats.put("density", gd.getDensity());
        inputStats.put("variant", gd.getVariant());
        inputStats.put("source", gd.getSource());
        result.set("input_stats", inputStats);

        ObjectNode sccNode = mapper.createObjectNode();
        sccNode.put("num_sccs", sccRes.components().size());
        ArrayNode sccsArr = mapper.createArrayNode();
        for (List<Integer> comp : sccRes.components()) {
            ArrayNode c = mapper.createArrayNode();
            for (int v : comp) c.add(v);
            sccsArr.add(c);
        }
        sccNode.set("sccs", sccsArr);
        sccNode.put("operations_count", sccOps);
        sccNode.put("execution_time_ms", sccTimeMs);
        result.set("tarjan_scc", sccNode);

        ObjectNode condNode = mapper.createObjectNode();
        condNode.put("vertices", cond.componentsCount);
        condNode.put("edges", count(cond.dag).edges());
        result.set("condensation_graph", condNode);

        ObjectNode topoNode = mapper.createObjectNode();
        ArrayNode topoArr = mapper.createArrayNode();
        for (int v : topoOrder) topoArr.add(v);
        topoNode.set("topological_order", topoArr);
        topoNode.put("operations_count", topoOps);
        topoNode.put("execution_time_ms", topoTimeMs);
        result.set("topological_sort", topoNode);

        ObjectNode spNode = mapper.createObjectNode();
        spNode.put("source", sourceComp);
        ArrayNode spPathArr = mapper.createArrayNode();
        for (int v : spPath) spPathArr.add(v);
        spNode.set("path", spPathArr);
        spNode.put("path_length", best);
        spNode.put("operations_count", spOps);
        spNode.put("execution_time_ms", spTimeMs);
        spNode.put("total_operations_count", sccOps + topoOps + spOps);
        spNode.put("total_execution_time_ms", sccTimeMs + topoTimeMs + spTimeMs);
        result.set("shortest_path", spNode);

        ObjectNode lpNode = mapper.createObjectNode();
        lpNode.put("critical_path_length", bestL);
        ArrayNode lpPathArr = mapper.createArrayNode();
        for (int v : lpPath) lpPathArr.add(v);
        lpNode.set("critical_path", lpPathArr);
        lpNode.put("operations_count", lpOps);
        lpNode.put("execution_time_ms", lpTimeMs);
        lpNode.put("total_operations_count", sccOps + topoOps + lpOps);
        lpNode.put("total_execution_time_ms", sccTimeMs + topoTimeMs + lpTimeMs);
        result.set("longest_path", lpNode);

        String base = gd.getId() + ";" + s.vertices() + ";" + s.edges() + ";" + gd.getDensity() + ";" + gd.getVariant() + ";";
        csvRows.append(base).append("Tarjan_SCC;").append(sccOps).append(";").append(String.format("%.6f", sccTimeMs)).append(";\n");
        csvRows.append(base).append("Topo_Kahn;").append(topoOps).append(";").append(String.format("%.6f", topoTimeMs)).append(";\n");
        csvRows.append(base).append("DAG_Shortest;").append(spOps).append(";").append(String.format("%.6f", spTimeMs)).append(";").append(String.format("%.6f", best)).append("\n");
        csvRows.append(base).append("DAG_Longest;").append(lpOps).append(";").append(String.format("%.6f", lpTimeMs)).append(";").append(String.format("%.6f", bestL)).append("\n");

        return result;
    }

    private static void runBatchOnInput(String inputPath, String outputPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<InputReader.GraphData> graphs = InputReader.loadAllGraphs(inputPath);
        ObjectNode root = mapper.createObjectNode();
        ArrayNode results = mapper.createArrayNode();
        StringBuilder csvRows = new StringBuilder();
        for (InputReader.GraphData gd : graphs) {
            results.add(runSingle(mapper, gd, csvRows));
        }
        root.set("results", results);

        File dir = new File("data");
        if (!dir.exists()) dir.mkdirs();
        mapper.writer(new DefaultPrettyPrinter()).writeValue(new File(outputPath), root);

        String[] lines = csvRows.toString().split("\n");
        List<String[]> rows = new ArrayList<>();
        for (String l : lines) {
            if (l.isEmpty()) continue;
            String[] parts = l.split(";");
            if (parts.length == 7) {
                rows.add(new String[]{parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6], "", ""});
            } else if (parts.length >= 8) {
                String pathLen = parts.length >= 9 ? parts[8] : "";
                rows.add(new String[]{parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6], parts[7], pathLen});
            }
        }
        boolean append = new File("data/output.csv").exists();
        Metrics.writeCsv("data/output.csv", rows.toArray(new String[0][]), append);
    }

    private static void runSingleFile(String path) throws Exception {
        File f = new File(path);
        if (!f.exists()) {
            System.err.println("Input JSON not found: " + f.getAbsolutePath());
            System.exit(1);
        }
        var loaded = JsonGraphLoader.load(f);
        WeightedDirectedGraph g = loaded.graph;
        var metrics = new SimpleMetrics();

        var tarjan = new TarjanSCC(g, metrics);
        var sccRes = tarjan.run();
        System.out.println("SCC components count: " + sccRes.components().size());
        for (int i = 0; i < sccRes.components().size(); i++) {
            System.out.println(" SCC " + i + ": " + sccRes.components().get(i));
        }

        Condensation.Result cond = Condensation.build(g, sccRes);
        System.out.println("Condensation DAG nodes: " + cond.componentsCount + ", edges aggregated.");

        List<Integer> topo = TopoSort.kahn(cond.dag, metrics);
        System.out.println("Topological order of components: " + topo);

        int sourceComponent = 0;
        if (loaded.source != null) {
            sourceComponent = sccRes.comp()[loaded.source];
        }
        var sp = DagShortestPaths.shortestPaths(cond.dag, sourceComponent, metrics);
        var lp = DagShortestPaths.longestPaths(cond.dag, sourceComponent, metrics);
        System.out.println("Shortest distances from comp " + sourceComponent + ":");
        for (int i = 0; i < sp.dist.length; i++) {
            System.out.println(" to comp " + i + " = " + sp.dist[i]);
        }
        System.out.println("Longest distances from comp " + sourceComponent + ":");
        for (int i = 0; i < lp.dist.length; i++) {
            System.out.println(" to comp " + i + " = " + lp.dist[i]);
        }

        if (!topo.isEmpty()) {
            int target = topo.get(topo.size() - 1);
            int[] compPath = sp.reconstructPath(target);
            System.out.print("One shortest path (components) to last topo node: ");
            for (int i = 0; i < compPath.length; i++) System.out.print((i > 0 ? "->" : "") + compPath[i]);
            System.out.println();
        }

        System.out.println("Metrics (selected):");
        System.out.println(" scc time ns: " + metrics.get("scc:timeNs"));
        System.out.println(" scc dfs calls: " + metrics.get("scc.dfs.calls"));
        System.out.println(" topo kahn time ns: " + metrics.get("topo.kahn:timeNs"));
        System.out.println(" dag sp time ns: " + metrics.get("dag.sp:timeNs"));
        System.out.println(" dag lp time ns: " + metrics.get("dag.lp:timeNs"));
    }


    public static void main(String[] args) {
        try {
            if (args.length > 0) {
                String path = args[0];
                runSingleFile(path);
            } else {

                File sparse = new File("data/input_sparse.json");
                File dense = new File("data/input_dense.json");
                if (!sparse.exists() || !dense.exists()) {
                    System.out.println("Inputs not found. Generating datasets...");
                    new InputJsonGenerator().generateAll();
                }

                System.out.println("Processing sparse graphs...");
                runBatchOnInput("data/input_sparse.json", "data/output_sparse.json");
                System.out.println("Processing dense graphs...");
                runBatchOnInput("data/input_dense.json", "data/output_dense.json");

                System.out.println();
                System.out.println("All done!");
                System.out.println("Wrote:");
                System.out.println("  • data/output_sparse.json");
                System.out.println("  • data/output_dense.json");
                System.out.println("  • data/output.csv");
                System.out.println();
                System.out.println("Check the report for analysis and conclusions.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Experiment failed: " + e.getMessage());
            System.exit(1);
        }
    }
}