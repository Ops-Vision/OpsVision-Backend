package com.opsvision.recovery.dto;

import com.opsvision.recovery.model.RecoveryAction;

import java.time.Instant;
import java.util.List;

public record RecoveryRecommendationResponse(
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
}
