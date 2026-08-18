package com.opsvision.observability.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.observability.exception.ObservabilityException;
import com.opsvision.observability.model.KubernetesEventSnapshot;
import com.opsvision.observability.model.PodSnapshot;
import com.opsvision.observability.model.WorkloadSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Kubernetes API client via Spring {@link RestClient} (apps/v1 Deployments, core/v1 Pods &amp; Events).
 * Avoids the heavy official client JAR while remaining mockable at the interface boundary.
 */
public class RestClientKubernetesApiClient implements KubernetesApiClient {

    public static final String NAME = "rest";

    private static final Logger log = LoggerFactory.getLogger(RestClientKubernetesApiClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestClientKubernetesApiClient(RestClient restClient, ObjectMapper objectMapper) {
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
    public List<WorkloadSnapshot> listDeployments(String namespace, String workloadName) {
        String ns = requireNamespace(namespace);
        try {
            if (workloadName != null && !workloadName.isBlank()) {
                String raw = get(ub -> ub
                        .path("/apis/apps/v1/namespaces/{namespace}/deployments/{name}")
                        .build(ns, workloadName.trim()));
                JsonNode item = objectMapper.readTree(raw);
                WorkloadSnapshot one = mapDeployment(item);
                return one == null ? List.of() : List.of(one);
            }
            String raw = get(ub -> ub
                    .path("/apis/apps/v1/namespaces/{namespace}/deployments")
                    .build(ns));
            JsonNode root = objectMapper.readTree(raw);
            List<WorkloadSnapshot> out = new ArrayList<>();
            for (JsonNode item : items(root)) {
                WorkloadSnapshot snap = mapDeployment(item);
                if (snap != null) {
                    out.add(snap);
                }
            }
            return List.copyOf(out);
        } catch (ObservabilityException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ObservabilityException("Failed to list Kubernetes deployments: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<PodSnapshot> listPods(String namespace, String labelSelector) {
        String ns = requireNamespace(namespace);
        try {
            String raw = get(ub -> {
                ub.path("/api/v1/namespaces/{namespace}/pods");
                if (labelSelector != null && !labelSelector.isBlank()) {
                    ub.queryParam("labelSelector", labelSelector);
                }
                return ub.build(ns);
            });
            JsonNode root = objectMapper.readTree(raw);
            List<PodSnapshot> out = new ArrayList<>();
            for (JsonNode item : items(root)) {
                PodSnapshot snap = mapPod(item);
                if (snap != null) {
                    out.add(snap);
                }
            }
            return List.copyOf(out);
        } catch (ObservabilityException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ObservabilityException("Failed to list Kubernetes pods: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<KubernetesEventSnapshot> listEvents(String namespace, String fieldSelector) {
        String ns = requireNamespace(namespace);
        try {
            String raw = get(ub -> {
                ub.path("/api/v1/namespaces/{namespace}/events");
                if (fieldSelector != null && !fieldSelector.isBlank()) {
                    ub.queryParam("fieldSelector", fieldSelector);
                }
                return ub.build(ns);
            });
            JsonNode root = objectMapper.readTree(raw);
            List<KubernetesEventSnapshot> out = new ArrayList<>();
            for (JsonNode item : items(root)) {
                KubernetesEventSnapshot snap = mapEvent(item);
                if (snap != null) {
                    out.add(snap);
                }
            }
            return List.copyOf(out);
        } catch (ObservabilityException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ObservabilityException("Failed to list Kubernetes events: " + ex.getMessage(), ex);
        }
    }

    private String get(Function<UriBuilder, URI> uriFunction) {
        try {
            String body = restClient.get()
                    .uri(uriFunction)
                    .retrieve()
                    .body(String.class);
            return body == null ? "{}" : body;
        } catch (RestClientResponseException ex) {
            throw new ObservabilityException(
                    "Kubernetes API HTTP " + ex.getStatusCode().value() + ": " + shorten(ex.getResponseBodyAsString()),
                    ex
            );
        } catch (RestClientException ex) {
            throw new ObservabilityException("Kubernetes API request failed: " + ex.getMessage(), ex);
        }
    }

    private static Iterable<JsonNode> items(JsonNode root) {
        JsonNode items = root == null ? null : root.path("items");
        if (items == null || !items.isArray()) {
            return List.of();
        }
        return items;
    }

    private WorkloadSnapshot mapDeployment(JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return null;
        }
        String name = text(item.path("metadata").path("name"), "unknown");
        String ns = text(item.path("metadata").path("namespace"), "default");
        JsonNode spec = item.path("spec");
        JsonNode status = item.path("status");
        int desired = intOr(spec.path("replicas"), 0);
        int ready = intOr(status.path("readyReplicas"), 0);
        int available = intOr(status.path("availableReplicas"), 0);
        int updated = intOr(status.path("updatedReplicas"), 0);
        int unavailable = intOr(status.path("unavailableReplicas"), 0);
        String image = firstContainerImage(spec);
        String rollout = deriveRolloutStatus(status, desired, ready, updated, unavailable);
        return new WorkloadSnapshot(
                name, ns, "Deployment",
                desired, ready, available, updated, unavailable,
                rollout, image
        );
    }

    private static String firstContainerImage(JsonNode spec) {
        JsonNode containers = spec.path("template").path("spec").path("containers");
        if (containers.isArray() && !containers.isEmpty()) {
            return text(containers.get(0).path("image"), null);
        }
        return null;
    }

    private static String deriveRolloutStatus(
            JsonNode status,
            int desired,
            int ready,
            int updated,
            int unavailable
    ) {
        JsonNode conditions = status.path("conditions");
        if (conditions.isArray()) {
            for (JsonNode c : conditions) {
                String type = text(c.path("type"), "");
                String condStatus = text(c.path("status"), "");
                if ("Progressing".equals(type) && "False".equalsIgnoreCase(condStatus)) {
                    return "ProgressingStuck:" + text(c.path("reason"), "Unknown");
                }
                if ("Available".equals(type) && "True".equalsIgnoreCase(condStatus)
                        && desired > 0 && ready >= desired && unavailable == 0) {
                    return "Available";
                }
            }
        }
        if (desired <= 0) {
            return "ScaledToZero";
        }
        if (updated < desired) {
            return "RollingUpdate";
        }
        if (ready < desired || unavailable > 0) {
            return "Degraded";
        }
        return "Available";
    }

    private PodSnapshot mapPod(JsonNode item) {
        if (item == null || item.isMissingNode()) {
            return null;
        }
        String name = text(item.path("metadata").path("name"), "unknown");
        String ns = text(item.path("metadata").path("namespace"), "default");
        JsonNode status = item.path("status");
        String phase = text(status.path("phase"), "Unknown");
        String reason = text(status.path("reason"), null);
        String node = text(status.path("nodeName"), null);
        Instant start = parseInstant(text(status.path("startTime"), null));

        int restarts = 0;
        int readyContainers = 0;
        int totalContainers = 0;
        JsonNode containerStatuses = status.path("containerStatuses");
        if (containerStatuses.isArray()) {
            for (JsonNode cs : containerStatuses) {
                totalContainers++;
                restarts += intOr(cs.path("restartCount"), 0);
                if (cs.path("ready").asBoolean(false)) {
                    readyContainers++;
                }
                if (reason == null || reason.isBlank()) {
                    JsonNode waiting = cs.path("state").path("waiting");
                    if (!waiting.isMissingNode() && !waiting.isNull()) {
                        reason = text(waiting.path("reason"), reason);
                    }
                }
            }
        }
        String ready = totalContainers == 0 ? "0/0" : readyContainers + "/" + totalContainers;
        return new PodSnapshot(name, ns, phase, ready, restarts, reason, node, start);
    }

    private KubernetesEventSnapshot mapEvent(JsonNode item) {
        if (item == null || item.isMissingNode()) {
            return null;
        }
        String name = text(item.path("metadata").path("name"), "unknown");
        String ns = text(item.path("metadata").path("namespace"), "default");
        String type = text(item.path("type"), "Normal");
        String reason = text(item.path("reason"), "Unknown");
        String message = text(item.path("message"), "");
        JsonNode involved = item.path("involvedObject");
        String involvedKind = text(involved.path("kind"), null);
        String involvedName = text(involved.path("name"), null);
        int count = intOr(item.path("count"), 1);
        Instant last = parseInstant(text(item.path("lastTimestamp"), null));
        if (last == null) {
            last = parseInstant(text(item.path("eventTime"), null));
        }
        if (last == null) {
            last = parseInstant(text(item.path("metadata").path("creationTimestamp"), null));
        }
        return new KubernetesEventSnapshot(
                name, ns, type, reason, message, involvedKind, involvedName, count, last
        );
    }

    private static String requireNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return "default";
        }
        return namespace.trim();
    }

    private static String text(JsonNode node, String defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        String v = node.asText(null);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return v;
    }

    private static int intOr(JsonNode node, int defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        return node.asInt(defaultValue);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            log.trace("Unparseable timestamp: {}", value);
            return null;
        }
    }

    private static String shorten(String body) {
        if (body == null) {
            return "";
        }
        String t = body.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "..." : t;
    }
}
