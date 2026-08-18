package com.opsvision.observability.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Normalized Kubernetes pod status for post-deployment monitoring.
 */
public record PodSnapshot(
        String name,
        String namespace,
        String phase,
        String ready,
        int restartCount,
        String reason,
        String nodeName,
        Instant startTime
) {
    public PodSnapshot {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(phase, "phase");
    }
}
