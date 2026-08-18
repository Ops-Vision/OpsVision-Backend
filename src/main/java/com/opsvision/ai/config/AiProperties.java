package com.opsvision.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM / AI provider configuration. Secrets bind from environment only.
 */
@ConfigurationProperties(prefix = "opsvision.ai")
public class AiProperties {

    /**
     * Master switch. When false, a no-op provider is used.
     */
    private boolean enabled = false;

    /**
     * Provider id: {@code none}, {@code openai-compatible}.
     */
    private String provider = "none";

    /**
     * OpenAI-compatible API base URL (e.g. https://api.openai.com/v1).
     */
    private String baseUrl = "https://api.openai.com/v1";

    /**
     * API key — never hardcode; bind from OPSVISION_AI_API_KEY.
     */
    private String apiKey = "";

    /**
     * Model id for chat completions.
     */
    private String model = "gpt-4o-mini";

    private int connectTimeoutMs = 5_000;

    private int readTimeoutMs = 60_000;

    private int maxTokens = 800;

    private double temperature = 0.2;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
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

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Whether a live OpenAI-compatible HTTP provider should be wired.
     */
    public boolean useOpenAiCompatible() {
        if (!enabled) {
            return false;
        }
        String p = provider == null ? "" : provider.trim().toLowerCase();
        return ("openai-compatible".equals(p) || "openai".equals(p)) && hasApiKey();
    }
}
