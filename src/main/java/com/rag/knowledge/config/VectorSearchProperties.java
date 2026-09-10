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
    private int candidateMultiplier = 5;
    private double minRelevanceScore = 0.18;
    private double diversityPenalty = 0.12;
    private double semanticConfidenceScore = 0.55;
    private double minKeywordCoverage = 0.25;

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

    public int getCandidateMultiplier() {
        return candidateMultiplier;
    }

    public void setCandidateMultiplier(int candidateMultiplier) {
        this.candidateMultiplier = candidateMultiplier;
    }

    public double getMinRelevanceScore() {
        return minRelevanceScore;
    }

    public void setMinRelevanceScore(double minRelevanceScore) {
        this.minRelevanceScore = minRelevanceScore;
    }

    public double getDiversityPenalty() {
        return diversityPenalty;
    }

    public void setDiversityPenalty(double diversityPenalty) {
        this.diversityPenalty = diversityPenalty;
    }

    public double getSemanticConfidenceScore() {
        return semanticConfidenceScore;
    }

    public void setSemanticConfidenceScore(double semanticConfidenceScore) {
        this.semanticConfidenceScore = semanticConfidenceScore;
    }

    public double getMinKeywordCoverage() {
        return minKeywordCoverage;
    }

    public void setMinKeywordCoverage(double minKeywordCoverage) {
        this.minKeywordCoverage = minKeywordCoverage;
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

    public int safeCandidateMultiplier() {
        return Math.max(2, Math.min(candidateMultiplier, 10));
    }

    public double safeMinRelevanceScore() {
        return Math.max(0, Math.min(minRelevanceScore, 1));
    }

    public double safeDiversityPenalty() {
        return Math.max(0, Math.min(diversityPenalty, 0.5));
    }

    public double safeSemanticConfidenceScore() {
        return Math.max(0, Math.min(semanticConfidenceScore, 1));
    }

    public double safeMinKeywordCoverage() {
        return Math.max(0, Math.min(minKeywordCoverage, 1));
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
