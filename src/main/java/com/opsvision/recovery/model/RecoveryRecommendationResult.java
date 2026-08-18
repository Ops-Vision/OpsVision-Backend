package com.opsvision.recovery.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic recovery recommendation for an incident. Does not execute any action.
 */
public record RecoveryRecommendationResult(
        Long incidentId,
        Instant recommendedAt,
        RecoveryAction action,
        String reason,
        String targetVersion,
        Long targetDeploymentId,
        String currentCommitSha,
        String topRcaCategory,
        double topRcaConfidence,
        boolean requiresHumanApproval,
        String executionMode,
        List<String> supportingEvidence,
        List<String> notes
) {
    public static final String EXECUTION_MODE_RECOMMENDATION_ONLY = "RECOMMENDATION_ONLY";

    public RecoveryRecommendationResult {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(recommendedAt, "recommendedAt");
        Objects.requireNonNull(action, "action");
        if (reason == null) {
            reason = "";
        }
        if (executionMode == null || executionMode.isBlank()) {
            executionMode = EXECUTION_MODE_RECOMMENDATION_ONLY;
        }
        if (supportingEvidence == null) {
            supportingEvidence = List.of();
        } else {
            supportingEvidence = List.copyOf(supportingEvidence);
        }
        if (notes == null) {
            notes = List.of();
        } else {
            notes = List.copyOf(notes);
        }
        if (Double.isNaN(topRcaConfidence) || topRcaConfidence < 0.0) {
            topRcaConfidence = 0.0;
        } else if (topRcaConfidence > 1.0) {
            topRcaConfidence = 1.0;
        }
        topRcaConfidence = Math.round(topRcaConfidence * 100.0) / 100.0;
    }
}
