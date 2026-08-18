package com.opsvision.observability.service;

import com.opsvision.observability.client.KubernetesApiClient;
import com.opsvision.observability.client.PrometheusClient;
import com.opsvision.observability.config.ObservabilityProperties;
import com.opsvision.observability.exception.ObservabilityException;
import com.opsvision.observability.model.KubernetesEventSnapshot;
import com.opsvision.observability.model.MetricSample;
import com.opsvision.observability.model.PodSnapshot;
import com.opsvision.observability.model.ServiceMetricsSnapshot;
import com.opsvision.observability.model.TelemetrySnapshot;
import com.opsvision.observability.model.WorkloadSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Collects and normalizes Kubernetes + Prometheus telemetry for post-deployment monitoring.
 * Does not perform incident detection or RCA (later steps).
 */
@Service
public class TelemetryCollectionService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryCollectionService.class);

    private final KubernetesApiClient kubernetesApiClient;
    private final PrometheusClient prometheusClient;
    private final ObservabilityProperties properties;

    public TelemetryCollectionService(
            KubernetesApiClient kubernetesApiClient,
            PrometheusClient prometheusClient,
            ObservabilityProperties properties
    ) {
        this.kubernetesApiClient = kubernetesApiClient;
        this.prometheusClient = prometheusClient;
        this.properties = properties;
    }

    /**
     * Collect telemetry using configured default namespace/workload.
     */
    public TelemetrySnapshot collect() {
        String ns = properties.getKubernetes().getDefaultNamespace();
        String workload = properties.getKubernetes().getDefaultWorkload();
        return collect(ns, blankToNull(workload));
    }

    /**
     * Collect telemetry for a namespace and optional deployment name.
     *
     * @param namespace    Kubernetes namespace (defaults to configured default)
     * @param workloadName optional Deployment name; when null, lists deployments in the namespace
     */
    public TelemetrySnapshot collect(String namespace, String workloadName) {
        String ns = (namespace == null || namespace.isBlank())
                ? properties.getKubernetes().getDefaultNamespace()
                : namespace.trim();
        String workload = blankToNull(workloadName);

        List<String> notes = new ArrayList<>();
        List<WorkloadSnapshot> workloads = List.of();
        List<PodSnapshot> pods = List.of();
        List<KubernetesEventSnapshot> events = List.of();
        boolean k8sOk = kubernetesApiClient.isAvailable();
        boolean promOk = prometheusClient.isAvailable();

        if (!properties.isEnabled()) {
            notes.add("Observability collection is disabled (opsvision.observability.enabled=false)");
            return new TelemetrySnapshot(
                    ns, workload, Instant.now(),
                    List.of(), List.of(), List.of(),
                    ServiceMetricsSnapshot.empty(),
                    false, false, notes
            );
        }

        if (k8sOk) {
            try {
                workloads = kubernetesApiClient.listDeployments(ns, workload);
                String labelSelector = buildPodLabelSelector(workload, workloads);
                pods = kubernetesApiClient.listPods(ns, labelSelector);
                String fieldSelector = workload == null
                        ? null
                        : "involvedObject.kind=Deployment,involvedObject.name=" + workload;
                events = kubernetesApiClient.listEvents(ns, fieldSelector);
                if (events.isEmpty() && workload != null) {
                    // fall back to namespace events when field selector yields nothing
                    events = kubernetesApiClient.listEvents(ns, null);
                }
            } catch (ObservabilityException ex) {
                log.warn("Kubernetes telemetry collection failed: {}", ex.getMessage());
                notes.add("Kubernetes collection failed: " + ex.getMessage());
                k8sOk = false;
            }
        } else {
            notes.add("Kubernetes client unavailable (" + kubernetesApiClient.name() + ")");
        }

        ServiceMetricsSnapshot metrics = ServiceMetricsSnapshot.empty();
        if (promOk) {
            try {
                metrics = collectMetrics();
                if (!metrics.hasData()) {
                    notes.add("Prometheus queries returned no samples (check query configuration)");
                }
            } catch (ObservabilityException ex) {
                log.warn("Prometheus telemetry collection failed: {}", ex.getMessage());
                notes.add("Prometheus collection failed: " + ex.getMessage());
                promOk = false;
            }
        } else {
            notes.add("Prometheus client unavailable (" + prometheusClient.name() + ")");
        }

        return new TelemetrySnapshot(
                ns,
                workload,
                Instant.now(),
                workloads,
                pods,
                events,
                metrics,
                k8sOk,
                promOk,
                notes
        );
    }

    private ServiceMetricsSnapshot collectMetrics() {
        ObservabilityProperties.Prometheus p = properties.getPrometheus();
        List<MetricSample> raw = new ArrayList<>();

        Double requestRate = queryAndAccumulate(resolveQuery(p.getRequestRateQuery(), defaultRequestRateQuery(p)),
                "request_rate", "1/s", raw);
        Double errorRate = queryAndAccumulate(resolveQuery(p.getErrorRateQuery(), defaultErrorRateQuery(p)),
                "error_rate", "1/s", raw);
        Double errorRatio = queryAndAccumulate(resolveQuery(p.getErrorRatioQuery(), defaultErrorRatioQuery(p)),
                "error_ratio", "ratio", raw);
        Double p50 = queryAndAccumulate(resolveQuery(p.getLatencyP50Query(), defaultLatencyQuery(p, 0.50)),
                "latency_p50", "s", raw);
        Double p95 = queryAndAccumulate(resolveQuery(p.getLatencyP95Query(), defaultLatencyQuery(p, 0.95)),
                "latency_p95", "s", raw);
        Double p99 = queryAndAccumulate(resolveQuery(p.getLatencyP99Query(), defaultLatencyQuery(p, 0.99)),
                "latency_p99", "s", raw);
        Double cpu = queryAndAccumulate(resolveQuery(p.getCpuQuery(), defaultCpuQuery(p)),
                "cpu_cores", "cores", raw);
        Double memory = queryAndAccumulate(resolveQuery(p.getMemoryQuery(), defaultMemoryQuery(p)),
                "memory_bytes", "bytes", raw);
        Double availability = queryAndAccumulate(resolveQuery(p.getAvailabilityQuery(), defaultAvailabilityQuery(p)),
                "availability", "ratio", raw);

        return new ServiceMetricsSnapshot(
                requestRate, errorRate, errorRatio,
                p50, p95, p99,
                cpu, memory, availability,
                raw
        );
    }

    private Double queryAndAccumulate(String query, String logicalName, String unit, List<MetricSample> raw) {
        if (query == null || query.isBlank()) {
            return null;
        }
        Optional<Double> value = prometheusClient.queryScalar(query);
        if (value.isEmpty() || value.get().isNaN()) {
            return null;
        }
        double v = value.get();
        raw.add(new MetricSample(logicalName, v, unit, Instant.now(), java.util.Map.of("query", query)));
        return v;
    }

    private static String resolveQuery(String configured, String fallback) {
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return fallback;
    }

    private static String selectorExpr(ObservabilityProperties.Prometheus p) {
        String sel = p.getServiceSelector();
        if (sel == null || sel.isBlank()) {
            return "";
        }
        String trimmed = sel.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        return "{" + trimmed + "}";
    }

    private static String defaultRequestRateQuery(ObservabilityProperties.Prometheus p) {
        String s = selectorExpr(p);
        if (s.isEmpty()) {
            return "";
        }
        return "sum(rate(http_server_requests_seconds_count" + s + "[5m]))";
    }

    private static String defaultErrorRateQuery(ObservabilityProperties.Prometheus p) {
        String s = selectorExpr(p);
        if (s.isEmpty()) {
            return "";
        }
        // status class 5xx when label present
        String inner = s.substring(0, s.length() - 1) + ",status=~\"5..\"}";
        return "sum(rate(http_server_requests_seconds_count" + inner + "[5m]))";
    }

    private static String defaultErrorRatioQuery(ObservabilityProperties.Prometheus p) {
        String req = defaultRequestRateQuery(p);
        String err = defaultErrorRateQuery(p);
        if (req.isEmpty() || err.isEmpty()) {
            return "";
        }
        return "(" + err + ") / (" + req + ")";
    }

    private static String defaultLatencyQuery(ObservabilityProperties.Prometheus p, double quantile) {
        String s = selectorExpr(p);
        if (s.isEmpty()) {
            return "";
        }
        return "histogram_quantile(" + quantile + ", sum(rate(http_server_requests_seconds_bucket" + s + "[5m])) by (le))";
    }

    private static String defaultCpuQuery(ObservabilityProperties.Prometheus p) {
        String s = selectorExpr(p);
        if (s.isEmpty()) {
            return "";
        }
        return "sum(rate(container_cpu_usage_seconds_total" + s + "[5m]))";
    }

    private static String defaultMemoryQuery(ObservabilityProperties.Prometheus p) {
        String s = selectorExpr(p);
        if (s.isEmpty()) {
            return "";
        }
        return "sum(container_memory_working_set_bytes" + s + ")";
    }

    private static String defaultAvailabilityQuery(ObservabilityProperties.Prometheus p) {
        String s = selectorExpr(p);
        if (s.isEmpty()) {
            return "";
        }
        // up metric when job/app labels match
        return "avg(up" + s + ")";
    }

    private static String buildPodLabelSelector(String workload, List<WorkloadSnapshot> workloads) {
        if (workload != null && !workload.isBlank()) {
            return "app=" + workload;
        }
        if (workloads != null && workloads.size() == 1) {
            return "app=" + workloads.getFirst().name();
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
