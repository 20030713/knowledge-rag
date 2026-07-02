package com.rag.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingModelProperties {

    private boolean enabled = false;
    private String baseUrl = "https://api.openai.com";
    private String endpointPath = "/v1/embeddings";
    private String apiKey = "";
    private String model = "text-embedding-3-small";
    private int timeoutSeconds = 30;
    private int dimensions = 1536;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEndpointPath() {
        return endpointPath;
    }

    public void setEndpointPath(String endpointPath) {
        this.endpointPath = endpointPath;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public boolean available() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public String safeBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com";
        }
        return baseUrl.replaceAll("/+$", "");
    }

    public String safeEndpointPath() {
        if (endpointPath == null || endpointPath.isBlank()) {
            return "/v1/embeddings";
        }
        return endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath;
    }

    public int safeTimeoutSeconds() {
        return Math.max(3, Math.min(timeoutSeconds, 120));
    }

    public int safeDimensions() {
        return Math.max(32, Math.min(dimensions, 8192));
    }
}
