package com.opsvision.observability.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.observability.exception.ObservabilityException;
import com.opsvision.observability.model.MetricSample;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prometheus HTTP API client ({@code /api/v1/query} instant queries).
 */
public class RestClientPrometheusClient implements PrometheusClient {

    public static final String NAME = "rest";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestClientPrometheusClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<MetricSample> query(String promQl) {
        if (promQl == null || promQl.isBlank()) {
            return List.of();
        }
        try {
            // Expand the PromQL as a URI-template variable so it is encoded
            // exactly once. Pre-encoding via UriComponentsBuilder and passing
            // the already-encoded string to .uri(String) caused double
            // encoding (%7B -> %257B), which made Prometheus reject every
            // selector query with HTTP 400 bad_data parse errors.
            String raw = restClient.get()
                    .uri("/api/v1/query?query={query}", promQl)
                    .retrieve()
                    .body(String.class);

            return parseQueryResponse(promQl.trim(), raw);
        } catch (RestClientResponseException ex) {
            throw new ObservabilityException(
                    "Prometheus HTTP " + ex.getStatusCode().value() + ": " + shorten(ex.getResponseBodyAsString()),
                    ex
            );
        } catch (RestClientException ex) {
            throw new ObservabilityException("Prometheus request failed: " + ex.getMessage(), ex);
        } catch (ObservabilityException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ObservabilityException("Prometheus query failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<Double> queryScalar(String promQl) {
        List<MetricSample> samples = query(promQl);
        if (samples.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(samples.getFirst().value());
    }

    private List<MetricSample> parseQueryResponse(String metricName, String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(raw);
        String status = root.path("status").asText("");
        if (!"success".equalsIgnoreCase(status)) {
            String err = root.path("error").asText("unknown error");
            throw new ObservabilityException("Prometheus query unsuccessful: " + err);
        }
        JsonNode data = root.path("data");
        String resultType = data.path("resultType").asText("");
        JsonNode result = data.path("result");
        List<MetricSample> out = new ArrayList<>();

        if ("scalar".equals(resultType) && result.isArray() && result.size() >= 2) {
            Instant ts = epochSeconds(result.get(0));
            double value = parseDouble(result.get(1).asText());
            out.add(new MetricSample(metricName, value, "", ts, Map.of()));
            return List.copyOf(out);
        }

        if (result.isArray()) {
            for (JsonNode series : result) {
                Map<String, String> labels = labels(series.path("metric"));
                String name = labels.getOrDefault("__name__", metricName);
                JsonNode valueNode = series.path("value");
                if (valueNode.isArray() && valueNode.size() >= 2) {
                    Instant ts = epochSeconds(valueNode.get(0));
                    double value = parseDouble(valueNode.get(1).asText());
                    out.add(new MetricSample(name, value, "", ts, labels));
                }
            }
        }
        return List.copyOf(out);
    }

    private static Map<String, String> labels(JsonNode metric) {
        Map<String, String> map = new HashMap<>();
        if (metric != null && metric.isObject()) {
            Iterator<String> names = metric.fieldNames();
            while (names.hasNext()) {
                String k = names.next();
                map.put(k, metric.path(k).asText(""));
            }
        }
        return map;
    }

    private static Instant epochSeconds(JsonNode node) {
        if (node == null || node.isNull()) {
            return Instant.now();
        }
        double sec = node.asDouble(Double.NaN);
        if (Double.isNaN(sec)) {
            try {
                sec = Double.parseDouble(node.asText("0"));
            } catch (NumberFormatException ex) {
                return Instant.now();
            }
        }
        long millis = Math.round(sec * 1000.0d);
        return Instant.ofEpochMilli(millis);
    }

    private static double parseDouble(String text) {
        if (text == null || text.isBlank() || "NaN".equalsIgnoreCase(text) || "+Inf".equals(text) || "-Inf".equals(text)) {
            return Double.NaN;
        }
        return Double.parseDouble(text);
    }

    private static String shorten(String body) {
        if (body == null) {
            return "";
        }
        String t = body.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "..." : t;
    }
}
