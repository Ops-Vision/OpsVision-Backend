package com.opsvision.observability.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Normalized Kubernetes warning/normal event.
 */
public record KubernetesEventSnapshot(
        String name,
        String namespace,
        String type,
        String reason,
        String message,
        String involvedKind,
        String involvedName,
        int count,
        Instant lastTimestamp
) {
    public KubernetesEventSnapshot {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reason, "reason");
        if (message == null) {
            message = "";
        }
    }

    public boolean isWarning() {
        return "Warning".equalsIgnoreCase(type);
    }
}
