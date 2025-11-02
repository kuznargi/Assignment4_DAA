package org.example.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.example.graph.common.WeightedDirectedGraph;


import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses JSON input files containing graph definitions.
 */
public class InputReader {

    public static List<GraphData> loadAllGraphs(String filepath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (FileInputStream in = new FileInputStream(filepath)) {
            JsonNode root = mapper.readTree(in);
            if (root == null || !root.has("graphs")) {
                throw new IOException("Invalid JSON: no 'graphs' array");
            }
            ArrayNode graphsArray = (ArrayNode) root.get("graphs");
            List<GraphData> graphsList = new ArrayList<>();
            for (JsonNode gnode : graphsArray) {
                int id = gnode.path("id").asInt();
                int n = gnode.path("n").asInt();
                if (n <= 0) throw new IllegalArgumentException("Vertices must be > 0");
                int source = gnode.has("source") ? gnode.get("source").asInt() : 0;
                if (source < 0 || source >= n) throw new IllegalArgumentException("Source out of bounds");
                String density = gnode.has("density") ? gnode.get("density").asText() : "unknown";
                String variant = gnode.has("variant") ? gnode.get("variant").asText() : "unknown";

                WeightedDirectedGraph graph = new WeightedDirectedGraph(n);
                if (gnode.has("edges")) {
                    for (JsonNode e : gnode.get("edges")) {
                        int u = e.path("u").asInt();
                        int v = e.path("v").asInt();
                        double w = e.path("w").asDouble();
                        if (u < 0 || u >= n || v < 0 || v >= n) {
                            throw new IllegalArgumentException("Edge vertex out of bounds");
                        }
                        graph.addEdge(u, v, w);
                    }
                }
                graphsList.add(new GraphData(id, graph, source, density, variant));
            }
            return graphsList;
        }
    }

    public static class GraphData {
        private final int id;
        private final WeightedDirectedGraph graph;
        private final int source;
        private final String density;  // "sparse" or "dense"
        private final String variant;  // "pure_dag", "one_cycle", ...

        public GraphData(int id, WeightedDirectedGraph graph, int source, String density, String variant) {
            this.id = id;
            this.graph = graph;
            this.source = source;
            this.density = density;
            this.variant = variant;
        }

        public int getId() { return id; }
        public WeightedDirectedGraph getGraph() { return graph; }
        public int getSource() { return source; }
        public String getDensity() { return density; }
        public String getVariant() { return variant; }
    }
}
