package com.opsvision.observability.model;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated service-level metrics from Prometheus-compatible queries.
 */
public record ServiceMetricsSnapshot(
        Double requestRatePerSecond,
        Double errorRatePerSecond,
        Double errorRatio,
        Double latencyP50Seconds,
        Double latencyP95Seconds,
        Double latencyP99Seconds,
        Double cpuCores,
        Double memoryBytes,
        Double availabilityRatio,
        List<MetricSample> rawSamples
) {
    public ServiceMetricsSnapshot {
        if (rawSamples == null) {
            rawSamples = List.of();
        } else {
            rawSamples = List.copyOf(rawSamples);
        }
    }

    public static ServiceMetricsSnapshot empty() {
        return new ServiceMetricsSnapshot(
                null, null, null, null, null, null, null, null, null, List.of()
        );
    }

    public boolean hasData() {
        return requestRatePerSecond != null
                || errorRatePerSecond != null
                || errorRatio != null
                || latencyP95Seconds != null
                || cpuCores != null
                || memoryBytes != null
                || availabilityRatio != null
                || !rawSamples.isEmpty();
    }
}
