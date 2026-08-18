package com.opsvision.postmortem.dto;

import com.opsvision.recovery.model.RecoveryAction;

import java.time.Instant;
import java.util.List;

public record PostmortemResponse(
        Long incidentId,
        Instant generatedAt,
        String method,
        String title,
        String executiveSummary,
        String impactSummary,
        String severity,
        String status,
        String environment,
        String namespace,
        String workloadName,
        String commitSha,
        Long deploymentId,
        Instant detectedAt,
        Instant startedAt,
        Instant resolvedAt,
        Long durationMinutes,
        String rootCauseSummary,
        String topRcaCategory,
        double topRcaConfidence,
        List<String> contributingFactors,
        List<TimelineHighlightResponse> timelineHighlights,
        List<String> whatWentWell,
        List<String> whatWentWrong,
        List<String> actionItems,
        RecoveryAction recommendedRecoveryAction,
        String recommendedRecoveryReason,
        String recoveryTargetVersion,
        boolean recoveryRequiresHumanApproval,
        List<String> limitations,
        String markdownBody
) {
    public record TimelineHighlightResponse(
            Instant occurredAt,
            String entryType,
            String title,
            String detail
    ) {
    }
}
