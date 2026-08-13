package com.opsvision.github.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.github.client.GitHubApiClient;
import com.opsvision.github.client.RestClientGitHubApiClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(GitHubProperties.class)
public class GitHubClientConfig {

    public static final String GITHUB_REST_CLIENT = "gitHubRestClient";

    @Bean(name = GITHUB_REST_CLIENT)
    RestClient gitHubRestClient(GitHubProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getApiBaseUrl()))
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", "OpsVision-Backend");

        if (properties.hasToken()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getToken());
        }

        return builder.build();
    }

    @Bean
    GitHubApiClient gitHubApiClient(
            @Qualifier(GITHUB_REST_CLIENT) RestClient gitHubRestClient,
            ObjectMapper objectMapper
    ) {
        return new RestClientGitHubApiClient(gitHubRestClient, objectMapper);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "https://api.github.com";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
