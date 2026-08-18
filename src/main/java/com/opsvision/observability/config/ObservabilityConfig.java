package com.opsvision.observability.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.observability.client.KubernetesApiClient;
import com.opsvision.observability.client.NoOpKubernetesApiClient;
import com.opsvision.observability.client.NoOpPrometheusClient;
import com.opsvision.observability.client.PrometheusClient;
import com.opsvision.observability.client.RestClientKubernetesApiClient;
import com.opsvision.observability.client.RestClientPrometheusClient;
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
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityConfig {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityConfig.class);

    public static final String K8S_REST_CLIENT = "kubernetesRestClient";
    public static final String PROM_REST_CLIENT = "prometheusRestClient";

    @Bean(name = K8S_REST_CLIENT)
    RestClient kubernetesRestClient(ObservabilityProperties properties) {
        ObservabilityProperties.Kubernetes k8s = properties.getKubernetes();
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(k8s.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(k8s.getReadTimeoutMs()));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(trimTrailingSlash(k8s.getApiBaseUrl()))
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "OpsVision-Backend");

        if (k8s.hasToken()) {
            builder.defaultHeader("Authorization", "Bearer " + k8s.getToken());
        }
        return builder.build();
    }

    @Bean(name = PROM_REST_CLIENT)
    RestClient prometheusRestClient(ObservabilityProperties properties) {
        ObservabilityProperties.Prometheus prom = properties.getPrometheus();
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(prom.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(prom.getReadTimeoutMs()));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        return RestClient.builder()
                .baseUrl(trimTrailingSlash(prom.getBaseUrl()))
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "OpsVision-Backend")
                .build();
    }

    @Bean
    KubernetesApiClient kubernetesApiClient(
            ObservabilityProperties properties,
            @Qualifier(K8S_REST_CLIENT) RestClient kubernetesRestClient,
            ObjectMapper objectMapper
    ) {
        if (!properties.isEnabled() || !properties.getKubernetes().isEnabled()) {
            log.info("Kubernetes observability client disabled (noop)");
            return new NoOpKubernetesApiClient();
        }
        log.info("Kubernetes observability client enabled (rest) baseUrl={}",
                properties.getKubernetes().getApiBaseUrl());
        return new RestClientKubernetesApiClient(kubernetesRestClient, objectMapper);
    }

    @Bean
    PrometheusClient prometheusClient(
            ObservabilityProperties properties,
            @Qualifier(PROM_REST_CLIENT) RestClient prometheusRestClient,
            ObjectMapper objectMapper
    ) {
        if (!properties.isEnabled() || !properties.getPrometheus().isEnabled()) {
            log.info("Prometheus observability client disabled (noop)");
            return new NoOpPrometheusClient();
        }
        log.info("Prometheus observability client enabled (rest) baseUrl={}",
                properties.getPrometheus().getBaseUrl());
        return new RestClientPrometheusClient(prometheusRestClient, objectMapper);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
