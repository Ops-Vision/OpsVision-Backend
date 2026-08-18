package com.opsvision.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.ai.provider.AiProvider;
import com.opsvision.ai.provider.NoOpAiProvider;
import com.opsvision.ai.provider.OllamaAiProvider;
import com.opsvision.ai.provider.OpenAiCompatibleAiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    public static final String AI_REST_CLIENT = "aiRestClient";
    public static final String OLLAMA_REST_CLIENT = "ollamaRestClient";

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean(name = AI_REST_CLIENT)
    RestClient aiRestClient(AiProperties properties) {
        ClientHttpRequestFactory requestFactory = requestFactory(properties);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl(), "https://api.openai.com/v1"))
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", "OpsVision-Backend");

        if (properties.hasApiKey()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getApiKey());
        }

        return builder.build();
    }

    @Bean(name = OLLAMA_REST_CLIENT)
    RestClient ollamaRestClient(AiProperties properties) {
        ClientHttpRequestFactory requestFactory = requestFactory(properties);
        return RestClient.builder()
                .baseUrl(properties.resolveOllamaBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", "OpsVision-Backend")
                .build();
    }

    @Bean
    AiProvider aiProvider(
            AiProperties properties,
            @Qualifier(AI_REST_CLIENT) RestClient aiRestClient,
            @Qualifier(OLLAMA_REST_CLIENT) RestClient ollamaRestClient,
            ObjectMapper objectMapper
    ) {
        if (properties.useOllama()) {
            log.info(
                    "AI provider: ollama baseUrl={} model={}",
                    properties.resolveOllamaBaseUrl(),
                    properties.resolveOllamaModel()
            );
            return new OllamaAiProvider(ollamaRestClient, properties, objectMapper);
        }
        if (properties.useOpenAiCompatible()) {
            log.info("AI provider: openai-compatible model={}", properties.getModel());
            return new OpenAiCompatibleAiProvider(aiRestClient, properties, objectMapper);
        }
        if (properties.isEnabled()) {
            String p = properties.getProvider() == null ? "" : properties.getProvider().trim().toLowerCase();
            if ("openai-compatible".equals(p) || "openai".equals(p)) {
                log.warn("AI enabled for OpenAI-compatible provider but API key missing; using no-op provider");
                return new NoOpAiProvider(
                        "AI is enabled but OPSVISION_AI_API_KEY is not configured. Explanations are unavailable."
                );
            }
            log.warn("AI enabled but provider '{}' is not configured; using no-op provider", properties.getProvider());
            return new NoOpAiProvider(
                    "AI is enabled but provider is not recognized or incomplete. Explanations are unavailable."
            );
        }
        log.info("AI provider: none (disabled or provider=none)");
        return new NoOpAiProvider();
    }

    private static ClientHttpRequestFactory requestFactory(AiProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return ClientHttpRequestFactories.get(settings);
    }

    private static String trimTrailingSlash(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
