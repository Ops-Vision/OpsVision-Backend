package com.opsvision.observability.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Single Prometheus (or compatible) metric sample after normalization.
 */
public record MetricSample(
        String name,
        double value,
        String unit,
        Instant timestamp,
        Map<String, String> labels
) {
    public MetricSample {
        Objects.requireNonNull(name, "name");
        if (unit == null) {
            unit = "";
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (labels == null) {
            labels = Map.of();
        } else {
            labels = Map.copyOf(labels);
        }
    }
}
