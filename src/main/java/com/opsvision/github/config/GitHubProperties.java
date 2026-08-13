package com.opsvision.github.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for GitHub REST API access.
 * Credentials and defaults come from environment / application properties — never hardcode tokens.
 */
@Validated
@ConfigurationProperties(prefix = "opsvision.github")
public class GitHubProperties {

    /**
     * Personal access token or GitHub App installation token.
     * Bound from GITHUB_API_TOKEN (avoid GITHUB_TOKEN name collision with CI reserved vars).
     */
    private String token = "";

    /**
     * Default organization or user that owns repositories.
     */
    private String owner = "";

    /**
     * Default repository name when not supplied per-call.
     */
    private String repository = "";

    /**
     * GitHub API base URL (override for GitHub Enterprise).
     */
    @NotBlank
    private String apiBaseUrl = "https://api.github.com";

    /**
     * HTTP connect timeout in milliseconds.
     */
    private int connectTimeoutMs = 5_000;

    /**
     * HTTP read timeout in milliseconds.
     */
    private int readTimeoutMs = 15_000;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
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

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    public String requireOwner() {
        if (owner == null || owner.isBlank()) {
            throw new IllegalStateException("opsvision.github.owner is not configured");
        }
        return owner;
    }

    public String requireRepository() {
        if (repository == null || repository.isBlank()) {
            throw new IllegalStateException("opsvision.github.repository is not configured");
        }
        return repository;
    }
}
