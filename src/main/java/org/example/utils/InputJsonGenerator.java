package org.example.utils;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Generates test graphs with various structures for performance evaluation.
 * Creates both sparse and dense variants for comparison.
 */
public class InputJsonGenerator {
    private final Random random;
    private final ObjectMapper mapper;

    public InputJsonGenerator() {
        this.random = new Random();
        this.mapper = new ObjectMapper();
    }


    private ObjectNode generateGraph(int id, int nodeCount, String variant, boolean isDense) {
        Set<String> edgeSet = new HashSet<>();
        ArrayNode edges = mapper.createArrayNode();

        int targetEdges;
        if (isDense) {
            targetEdges = Math.min(nodeCount * 4, Math.max(nodeCount, nodeCount * (nodeCount - 1) / 3));
        } else {
            targetEdges = Math.max(nodeCount, (int) (nodeCount * 1.8));
        }

        switch (variant) {
            case "pure_dag" -> generatePureDAG(nodeCount, targetEdges, edges, edgeSet);
            case "one_cycle" -> generateOneCycle(nodeCount, targetEdges, edges, edgeSet);
            case "two_cycles" -> generateTwoCycles(nodeCount, targetEdges, edges, edgeSet);
            case "mixed" -> generateSeveralSCCs(nodeCount, targetEdges, edges, edgeSet, 3, 5);
            case "many_sccs" -> generateSeveralSCCs(nodeCount, targetEdges, edges, edgeSet, 5, 10);
            default -> generatePureDAG(nodeCount, targetEdges, edges, edgeSet);
        }

        int source = getSmartSource(nodeCount, variant);

        ObjectNode graph = mapper.createObjectNode();
        graph.put("id", id);
        graph.put("directed", true);
        graph.put("n", nodeCount);
        graph.set("edges", edges);
        graph.put("source", source);
        graph.put("weight_model", "edge");
        graph.put("density", isDense ? "dense" : "sparse");
        graph.put("variant", variant);
        return graph;
    }

    private int getSmartSource(int nodeCount, String variant) {
        return switch (variant) {
            case "many_sccs" -> Math.max(0, nodeCount / 3);
            default -> 0;
        };
    }


    private void generatePureDAG(int n, int targetEdges, ArrayNode edges, Set<String> edgeSet) {
        int[] level = new int[n];
        int numLevels = Math.max(3, (int) Math.sqrt(n));

        for (int i = 0; i < n; i++) {
            level[i] = (i * numLevels) / n;
        }

        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                if (level[j] > level[i]) {
                    addEdge(i, j, edges, edgeSet);
                    break;
                }
            }
        }

        int attempts = 0;
        int maxAttempts = 20000;
        while (edges.size() < targetEdges && attempts < maxAttempts) {
            int u = random.nextInt(n);
            int v = random.nextInt(n);
            if (u != v && level[u] < level[v]) {
                addEdge(u, v, edges, edgeSet);
            }
            attempts++;
        }
    }


    private void generateOneCycle(int n, int targetEdges, ArrayNode edges, Set<String> edgeSet) {
        for (int i = 0; i < n - 1; i++) {
            addEdge(i, i + 1, edges, edgeSet);
        }
        addEdge(n - 1, 0, edges, edgeSet);

        int attempts = 0;
        int maxAttempts = 20000;
        while (edges.size() < targetEdges && attempts < maxAttempts) {
            int u = random.nextInt(n);
            int v = random.nextInt(n);
            if (u != v) addEdge(u, v, edges, edgeSet);
            attempts++;
        }
    }


    private void generateTwoCycles(int n, int targetEdges, ArrayNode edges, Set<String> edgeSet) {
        int split = Math.max(2, n / 2);

        for (int i = 0; i < split - 1; i++) addEdge(i, i + 1, edges, edgeSet);
        addEdge(split - 1, 0, edges, edgeSet);

        for (int i = split; i < n - 1; i++) addEdge(i, i + 1, edges, edgeSet);
        addEdge(n - 1, Math.min(n - 1, split), edges, edgeSet);

        // Connect cycles both ways occasionally
        addEdge(random.nextInt(split), split + random.nextInt(Math.max(1, n - split)), edges, edgeSet);

        int attempts = 0;
        int maxAttempts = 20000;
        while (edges.size() < targetEdges && attempts < maxAttempts) {
            int u = random.nextInt(n);
            int v = random.nextInt(n);
            boolean sameGroup = (u < split && v < split) || (u >= split && v >= split);
            if (u != v && sameGroup) addEdge(u, v, edges, edgeSet);
            attempts++;
        }
    }


    private void generateSeveralSCCs(int n, int targetEdges, ArrayNode edges,
                                     Set<String> edgeSet, int minSCCs, int maxSCCs) {
        int numSCCs = minSCCs + random.nextInt(Math.max(1, maxSCCs - minSCCs + 1));
        numSCCs = Math.max(1, Math.min(numSCCs, Math.max(1, n / 2)));

        List<List<Integer>> sccs = new ArrayList<>();
        int verticesPerSCC = Math.max(1, n / numSCCs);
        int remainder = n % numSCCs;
        int current = 0;

        for (int i = 0; i < numSCCs; i++) {
            int size = Math.max(1, verticesPerSCC + (i < remainder ? 1 : 0));
            List<Integer> scc = new ArrayList<>();
            for (int j = 0; j < size && current < n; j++) scc.add(current++);
            if (scc.isEmpty()) continue;
            sccs.add(scc);
        }

        for (List<Integer> scc : sccs) {
            if (scc.size() == 1) continue;
            for (int i = 0; i < scc.size() - 1; i++) addEdge(scc.get(i), scc.get(i + 1), edges, edgeSet);
            addEdge(scc.get(scc.size() - 1), scc.get(0), edges, edgeSet);
        }

        for (int i = 0; i < sccs.size() - 1; i++) {
            List<Integer> from = sccs.get(i);
            List<Integer> to = sccs.get(i + 1);
            int u = from.get(random.nextInt(from.size()));
            int v = to.get(random.nextInt(to.size()));
            addEdge(u, v, edges, edgeSet);
        }

        for (int i = 0; i < sccs.size() - 1; i++) {
            List<Integer> from = sccs.get(i);
            for (int j = i + 2; j < sccs.size() && edges.size() < targetEdges * 0.8; j++) {
                List<Integer> to = sccs.get(j);
                if (random.nextDouble() < 0.5) {
                    int u = from.get(random.nextInt(from.size()));
                    int v = to.get(random.nextInt(to.size()));
                    addEdge(u, v, edges, edgeSet);
                }
            }
        }

        int attempts = 0;
        int maxAttempts = 20000;
        while (edges.size() < targetEdges && attempts < maxAttempts) {
            int sccIdx = random.nextInt(Math.max(1, sccs.size()));
            List<Integer> scc = sccs.get(sccIdx);
            if (scc.size() > 1) {
                int u = scc.get(random.nextInt(scc.size()));
                int v = scc.get(random.nextInt(scc.size()));
                if (u != v) addEdge(u, v, edges, edgeSet);
            }
            attempts++;
        }
    }


    private void addEdge(int u, int v, ArrayNode edges, Set<String> edgeSet) {
        String key = u + "->" + v;
        if (!edgeSet.contains(key)) {
            ObjectNode edge = mapper.createObjectNode();
            edge.put("u", u);
            edge.put("v", v);
            double w = Math.round((1 + random.nextDouble() * 9) * 10.0) / 10.0; // 1.0..10.0, 1 decimal
            edge.put("w", w);
            edges.add(edge);
            edgeSet.add(key);
        }
    }

    private void generateAndSave(String filename, boolean isDense) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode graphs = mapper.createArrayNode();
        int id = 1;

        if (!isDense) {
            graphs.add(generateGraph(id++, 6, "pure_dag", false));
            graphs.add(generateGraph(id++, 8, "one_cycle", false));
            graphs.add(generateGraph(id++, 10, "two_cycles", false));
            graphs.add(generateGraph(id++, 12, "mixed", false));
            graphs.add(generateGraph(id++, 16, "mixed", false));
            graphs.add(generateGraph(id++, 20, "mixed", false));
            graphs.add(generateGraph(id++, 25, "many_sccs", false));
            graphs.add(generateGraph(id++, 35, "pure_dag", false));
            graphs.add(generateGraph(id++, 50, "many_sccs", false));
        } else {
            graphs.add(generateGraph(id++, 6, "pure_dag", true));
            graphs.add(generateGraph(id++, 8, "one_cycle", true));
            graphs.add(generateGraph(id++, 10, "two_cycles", true));
            graphs.add(generateGraph(id++, 12, "mixed", true));
            graphs.add(generateGraph(id++, 16, "mixed", true));
            graphs.add(generateGraph(id++, 20, "mixed", true));
            graphs.add(generateGraph(id++, 25, "many_sccs", true));
            graphs.add(generateGraph(id++, 35, "pure_dag", true));
            graphs.add(generateGraph(id++, 50, "many_sccs", true));
        }
        root.set("graphs", graphs);

        File dir = new File("data");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, filename);
        mapper.writer(new DefaultPrettyPrinter()).writeValue(out, root);
    }

    public void generateAll() throws IOException {
        generateAndSave("input_sparse.json", false);
        generateAndSave("input_dense.json", true);
    }

    public static void main(String[] args) {
        InputJsonGenerator generator = new InputJsonGenerator();
        try {
            generator.generateAll();
            System.out.println("Generated data/input_sparse.json and data/input_dense.json");
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
