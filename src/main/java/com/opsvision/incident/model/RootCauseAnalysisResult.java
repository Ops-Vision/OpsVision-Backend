package com.opsvision.incident.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic RCA output for an incident (not LLM-authored).
 */
public record RootCauseAnalysisResult(
        Long incidentId,
        Instant analyzedAt,
        String method,
        String summary,
        List<ProbableCause> probableCauses,
        List<String> limitations
) {
    public RootCauseAnalysisResult {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(analyzedAt, "analyzedAt");
        if (method == null || method.isBlank()) {
            method = "deterministic-correlation";
        }
        if (summary == null) {
            summary = "";
        }
        if (probableCauses == null) {
            probableCauses = List.of();
        } else {
            probableCauses = List.copyOf(probableCauses);
        }
        if (limitations == null) {
            limitations = List.of();
        } else {
            limitations = List.copyOf(limitations);
        }
    }
}
