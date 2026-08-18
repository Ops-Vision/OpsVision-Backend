package com.opsvision.ai.model;

import java.util.List;
import java.util.Objects;

/**
 * AI-generated deployment risk explanation. Never authoritative for score or policy.
 */
public record DeploymentExplanation(
        String summary,
        List<String> concerns,
        List<String> remediations,
        String provider,
        String model,
        boolean available
) {
    public DeploymentExplanation {
        Objects.requireNonNull(summary, "summary");
        if (concerns == null) {
            concerns = List.of();
        } else {
            concerns = List.copyOf(concerns);
        }
        if (remediations == null) {
            remediations = List.of();
        } else {
            remediations = List.copyOf(remediations);
        }
    }

    public static DeploymentExplanation unavailable(String provider, String message) {
        return new DeploymentExplanation(
                message != null ? message : "AI explanations are not available",
                List.of(),
                List.of(),
                provider,
                null,
                false
        );
    }
}
