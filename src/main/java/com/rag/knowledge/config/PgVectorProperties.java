package com.rag.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vector-store.pgvector")
public class PgVectorProperties {

    private boolean enabled = false;

    private String url = "jdbc:postgresql://localhost:5432/knowledge_vectors";

    private String username = "postgres";

    private String password = "postgres";

    private String tableName = "document_chunk_vector";

    private boolean initializeSchema = true;

    private int dimensions = 1024;

    private int candidateMultiplier = 4;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public boolean isInitializeSchema() {
        return initializeSchema;
    }

    public void setInitializeSchema(boolean initializeSchema) {
        this.initializeSchema = initializeSchema;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getCandidateMultiplier() {
        return candidateMultiplier;
    }

    public void setCandidateMultiplier(int candidateMultiplier) {
        this.candidateMultiplier = candidateMultiplier;
    }

    public int safeDimensions() {
        return dimensions > 0 ? dimensions : 1024;
    }

    public int safeCandidateMultiplier() {
        return Math.max(1, Math.min(candidateMultiplier, 10));
    }

    public String safeTableName() {
        String normalized = tableName == null || tableName.isBlank() ? "document_chunk_vector" : tableName.trim();
        if (!normalized.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return "document_chunk_vector";
        }
        return normalized;
    }
}
