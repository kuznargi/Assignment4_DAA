package org.example.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Tracks performance metrics for all algorithms.
 * Measures execution time and operation counts (comparisons, updates, etc.)
 */
public class Metrics {
   private final String algorithmName;

    public Metrics(String algorithmName) {
        this.algorithmName = algorithmName;
    }


    public static void writeCsv(String filePath, String[][] data, boolean append) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, append))) {
            if (!append) {
                writer.write("graph_id;vertices;edges;density;variant;algorithm;total_operations_count;total_execution_time_ms;path_length\n");
            }
            for (String[] row : data) {
                writer.write(String.join(";", row) + "\n");
            }
        } catch (IOException e) {
            System.err.println("Error writing CSV: " + e.getMessage());
            throw e;
        }
    }
}
