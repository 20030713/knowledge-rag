package com.rag.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.vector-search")
public class VectorSearchProperties {

    private int topK = 5;
    private int maxChunksToScan = 200;
    private int dimensions = 256;
    private double vectorWeight = 0.7;
    private double keywordWeight = 0.3;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getMaxChunksToScan() {
        return maxChunksToScan;
    }

    public void setMaxChunksToScan(int maxChunksToScan) {
        this.maxChunksToScan = maxChunksToScan;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public double getVectorWeight() {
        return vectorWeight;
    }

    public void setVectorWeight(double vectorWeight) {
        this.vectorWeight = vectorWeight;
    }

    public double getKeywordWeight() {
        return keywordWeight;
    }

    public void setKeywordWeight(double keywordWeight) {
        this.keywordWeight = keywordWeight;
    }

    public int safeTopK() {
        return Math.max(1, Math.min(topK, 20));
    }

    public int safeMaxChunksToScan() {
        return Math.max(safeTopK(), Math.min(maxChunksToScan, 1000));
    }

    public int safeDimensions() {
        return Math.max(32, Math.min(dimensions, 4096));
    }

    public double normalizedVectorWeight() {
        double total = vectorWeight + keywordWeight;
        if (total <= 0) {
            return 0.7;
        }
        return vectorWeight / total;
    }

    public double normalizedKeywordWeight() {
        double total = vectorWeight + keywordWeight;
        if (total <= 0) {
            return 0.3;
        }
        return keywordWeight / total;
    }
}
