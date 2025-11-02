package org.example.graph.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Loads a graph from a JSON file.
 * Expected format:
 * {
 *   "directed": true,
 *   "n": 8,
 *   "edges": [ {"u":0, "v":1, "w":3}, ... ],
 *   "source": 0,
 *   "weight_model": "edge" | "node"
 * }
 */
public class JsonGraphLoader {
    public static class LoadedGraph {
        public final WeightedDirectedGraph graph;
        public final Integer source;
        public final String weightModel;
        public LoadedGraph(WeightedDirectedGraph graph, Integer source, String weightModel) {
            this.graph = graph;
            this.source = source;
            this.weightModel = weightModel;
        }
    }

    public static LoadedGraph load(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        int n = root.get("n").asInt();
        List<Edge> edges = new ArrayList<>();
        JsonNode arr = root.get("edges");
        if (arr != null && arr.isArray()) {
            Iterator<JsonNode> it = arr.elements();
            while (it.hasNext()) {
                JsonNode e = it.next();
                int u = e.get("u").asInt();
                int v = e.get("v").asInt();
                double w = e.has("w") ? e.get("w").asDouble() : 1.0;
                edges.add(new Edge(u, v, w));
            }
        }
        WeightedDirectedGraph g = WeightedDirectedGraph.fromEdges(n, edges);
        Integer source = root.has("source") ? root.get("source").asInt() : null;
        String weightModel = root.has("weight_model") ? root.get("weight_model").asText() : "edge";
        return new LoadedGraph(g, source, weightModel);
    }
}
