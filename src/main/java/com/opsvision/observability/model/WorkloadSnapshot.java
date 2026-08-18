package com.opsvision.observability.model;

import java.util.Objects;

/**
 * Normalized Kubernetes Deployment / workload rollout status.
 */
public record WorkloadSnapshot(
        String name,
        String namespace,
        String kind,
        int desiredReplicas,
        int readyReplicas,
        int availableReplicas,
        int updatedReplicas,
        int unavailableReplicas,
        String rolloutStatus,
        String image
) {
    public WorkloadSnapshot {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(kind, "kind");
        if (rolloutStatus == null || rolloutStatus.isBlank()) {
            rolloutStatus = "Unknown";
        }
    }

    public boolean isHealthy() {
        return desiredReplicas > 0
                && readyReplicas >= desiredReplicas
                && unavailableReplicas == 0;
    }
}
