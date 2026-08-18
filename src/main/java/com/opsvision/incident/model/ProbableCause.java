package com.opsvision.incident.model;

import java.util.List;
import java.util.Objects;

/**
 * A ranked deterministic root-cause hypothesis with supporting evidence strings.
 */
public record ProbableCause(
        String cause,
        double confidence,
        String category,
        List<String> evidence
) {
    public ProbableCause {
        Objects.requireNonNull(cause, "cause");
        if (category == null || category.isBlank()) {
            category = "UNKNOWN";
        }
        if (evidence == null) {
            evidence = List.of();
        } else {
            evidence = List.copyOf(evidence);
        }
        if (Double.isNaN(confidence) || confidence < 0.0) {
            confidence = 0.0;
        } else if (confidence > 1.0) {
            confidence = 1.0;
        }
        // Round to 2 decimal places for stable API output
        confidence = Math.round(confidence * 100.0) / 100.0;
    }
}
