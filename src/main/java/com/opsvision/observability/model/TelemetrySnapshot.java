package com.opsvision.observability.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Combined post-deployment telemetry from Kubernetes and Prometheus.
 */
public record TelemetrySnapshot(
        String namespace,
        String workloadName,
        Instant collectedAt,
        List<WorkloadSnapshot> workloads,
        List<PodSnapshot> pods,
        List<KubernetesEventSnapshot> events,
        ServiceMetricsSnapshot metrics,
        boolean kubernetesAvailable,
        boolean prometheusAvailable,
        List<String> notes
) {
    public TelemetrySnapshot {
        Objects.requireNonNull(collectedAt, "collectedAt");
        if (workloads == null) {
            workloads = List.of();
        } else {
            workloads = List.copyOf(workloads);
        }
        if (pods == null) {
            pods = List.of();
        } else {
            pods = List.copyOf(pods);
        }
        if (events == null) {
            events = List.of();
        } else {
            events = List.copyOf(events);
        }
        if (metrics == null) {
            metrics = ServiceMetricsSnapshot.empty();
        }
        if (notes == null) {
            notes = List.of();
        } else {
            notes = List.copyOf(notes);
        }
    }

    public List<KubernetesEventSnapshot> warningEvents() {
        return events.stream().filter(KubernetesEventSnapshot::isWarning).toList();
    }

    public int totalPodRestarts() {
        return pods.stream().mapToInt(PodSnapshot::restartCount).sum();
    }
}
