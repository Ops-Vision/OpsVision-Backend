package com.opsvision.observability.dto;

import java.time.Instant;
import java.util.List;

/**
 * REST DTO for collected Kubernetes + Prometheus telemetry.
 */
public record TelemetryResponse(
        String namespace,
        String workloadName,
        Instant collectedAt,
        boolean kubernetesAvailable,
        boolean prometheusAvailable,
        int totalPodRestarts,
        List<WorkloadDto> workloads,
        List<PodDto> pods,
        List<EventDto> events,
        MetricsDto metrics,
        List<String> notes
) {
    public record WorkloadDto(
            String name,
            String namespace,
            String kind,
            int desiredReplicas,
            int readyReplicas,
            int availableReplicas,
            int updatedReplicas,
            int unavailableReplicas,
            String rolloutStatus,
            String image,
            boolean healthy
    ) {
    }

    public record PodDto(
            String name,
            String namespace,
            String phase,
            String ready,
            int restartCount,
            String reason,
            String nodeName,
            Instant startTime
    ) {
    }

    public record EventDto(
            String name,
            String namespace,
            String type,
            String reason,
            String message,
            String involvedKind,
            String involvedName,
            int count,
            Instant lastTimestamp,
            boolean warning
    ) {
    }

    public record MetricsDto(
            Double requestRatePerSecond,
            Double errorRatePerSecond,
            Double errorRatio,
            Double latencyP50Seconds,
            Double latencyP95Seconds,
            Double latencyP99Seconds,
            Double cpuCores,
            Double memoryBytes,
            Double availabilityRatio
    ) {
    }
}
