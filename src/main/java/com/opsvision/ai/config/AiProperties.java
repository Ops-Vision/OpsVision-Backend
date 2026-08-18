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
     * Provider id: {@code none}, {@code openai-compatible}, {@code ollama}.
     */
    private String provider = "none";

    /**
     * OpenAI-compatible API base URL (e.g. https://api.openai.com/v1).
     */
    private String baseUrl = "https://api.openai.com/v1";

    /**
     * API key for OpenAI-compatible providers — never hardcode; bind from OPSVISION_AI_API_KEY.
     */
    private String apiKey = "";

    /**
     * Model id for OpenAI-compatible chat completions.
     */
    private String model = "gpt-4o-mini";

    private int connectTimeoutMs = 5_000;

    private int readTimeoutMs = 60_000;

    private int maxTokens = 800;

    private double temperature = 0.2;

    /**
     * Native Ollama settings (used when provider=ollama).
     */
    private final Ollama ollama = new Ollama();

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

    public Ollama getOllama() {
        return ollama;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    private String normalizedProvider() {
        return provider == null ? "" : provider.trim().toLowerCase();
    }

    /**
     * Whether a live OpenAI-compatible HTTP provider should be wired.
     */
    public boolean useOpenAiCompatible() {
        if (!enabled) {
            return false;
        }
        String p = normalizedProvider();
        return ("openai-compatible".equals(p) || "openai".equals(p)) && hasApiKey();
    }

    /**
     * Whether the native Ollama provider should be wired (no API key required).
     */
    public boolean useOllama() {
        if (!enabled) {
            return false;
        }
        return "ollama".equals(normalizedProvider());
    }

    public String resolveOllamaBaseUrl() {
        String url = ollama.getBaseUrl();
        if (url == null || url.isBlank()) {
            return "http://localhost:11434";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String resolveOllamaModel() {
        String m = ollama.getModel();
        if (m == null || m.isBlank()) {
            return "llama3.2";
        }
        return m.trim();
    }

    /**
     * Nested Ollama-specific configuration.
     */
    public static class Ollama {

        /**
         * Ollama daemon base URL (native API root, not /v1).
         */
        private String baseUrl = "http://localhost:11434";

        /**
         * Model name as known to Ollama (e.g. llama3.2, mistral).
         */
        private String model = "llama3.2";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
