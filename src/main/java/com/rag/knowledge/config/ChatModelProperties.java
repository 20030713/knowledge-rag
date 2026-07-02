package com.rag.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.chat-model")
public class ChatModelProperties {

    private boolean enabled = false;
    private String baseUrl = "https://api.openai.com";
    private String endpointPath = "/v1/chat/completions";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private int timeoutSeconds = 30;
    private int maxContextChars = 6000;
    private double temperature = 0.2;
    private boolean thinkingEnabled = false;
    private String reasoningEffort = "";

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

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public void setThinkingEnabled(boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
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
            return "/v1/chat/completions";
        }
        return endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath;
    }

    public int safeTimeoutSeconds() {
        return Math.max(3, Math.min(timeoutSeconds, 120));
    }

    public int safeMaxContextChars() {
        return Math.max(1000, Math.min(maxContextChars, 20000));
    }

    public double safeTemperature() {
        return Math.max(0, Math.min(temperature, 2));
    }

    public String safeReasoningEffort() {
        if (reasoningEffort == null || reasoningEffort.isBlank()) {
            return null;
        }
        String normalized = reasoningEffort.trim().toLowerCase();
        if ("low".equals(normalized) || "medium".equals(normalized) || "high".equals(normalized)) {
            return normalized;
        }
        return null;
    }
}
