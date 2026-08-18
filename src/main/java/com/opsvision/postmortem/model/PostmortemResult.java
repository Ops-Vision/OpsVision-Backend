package com.opsvision.postmortem.model;

import com.opsvision.recovery.model.RecoveryAction;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic blameless postmortem assembled from incident, RCA, and recovery outputs.
 * Does not invent facts beyond provided structured inputs.
 */
public record PostmortemResult(
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
        List<TimelineHighlight> timelineHighlights,
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
    public static final String METHOD = "deterministic-template";

    public PostmortemResult {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(generatedAt, "generatedAt");
        if (method == null || method.isBlank()) {
            method = METHOD;
        }
        if (title == null) {
            title = "";
        }
        if (executiveSummary == null) {
            executiveSummary = "";
        }
        if (impactSummary == null) {
            impactSummary = "";
        }
        if (rootCauseSummary == null) {
            rootCauseSummary = "";
        }
        if (contributingFactors == null) {
            contributingFactors = List.of();
        } else {
            contributingFactors = List.copyOf(contributingFactors);
        }
        if (timelineHighlights == null) {
            timelineHighlights = List.of();
        } else {
            timelineHighlights = List.copyOf(timelineHighlights);
        }
        if (whatWentWell == null) {
            whatWentWell = List.of();
        } else {
            whatWentWell = List.copyOf(whatWentWell);
        }
        if (whatWentWrong == null) {
            whatWentWrong = List.of();
        } else {
            whatWentWrong = List.copyOf(whatWentWrong);
        }
        if (actionItems == null) {
            actionItems = List.of();
        } else {
            actionItems = List.copyOf(actionItems);
        }
        if (limitations == null) {
            limitations = List.of();
        } else {
            limitations = List.copyOf(limitations);
        }
        if (markdownBody == null) {
            markdownBody = "";
        }
        if (Double.isNaN(topRcaConfidence) || topRcaConfidence < 0.0) {
            topRcaConfidence = 0.0;
        } else if (topRcaConfidence > 1.0) {
            topRcaConfidence = 1.0;
        }
        topRcaConfidence = Math.round(topRcaConfidence * 100.0) / 100.0;
    }

    public record TimelineHighlight(
            Instant occurredAt,
            String entryType,
            String title,
            String detail
    ) {
        public TimelineHighlight {
            if (entryType == null) {
                entryType = "UNKNOWN";
            }
            if (title == null) {
                title = "";
            }
            if (detail == null) {
                detail = "";
            }
        }
    }
}
