package com.opsvision.postmortem.mapper;

import com.opsvision.postmortem.dto.PostmortemResponse;
import com.opsvision.postmortem.model.PostmortemResult;
import org.springframework.stereotype.Component;

@Component
public class PostmortemMapper {

    public PostmortemResponse toResponse(PostmortemResult result) {
        return new PostmortemResponse(
                result.incidentId(),
                result.generatedAt(),
                result.method(),
                result.title(),
                result.executiveSummary(),
                result.impactSummary(),
                result.severity(),
                result.status(),
                result.environment(),
                result.namespace(),
                result.workloadName(),
                result.commitSha(),
                result.deploymentId(),
                result.detectedAt(),
                result.startedAt(),
                result.resolvedAt(),
                result.durationMinutes(),
                result.rootCauseSummary(),
                result.topRcaCategory(),
                result.topRcaConfidence(),
                result.contributingFactors(),
                result.timelineHighlights().stream()
                        .map(h -> new PostmortemResponse.TimelineHighlightResponse(
                                h.occurredAt(),
                                h.entryType(),
                                h.title(),
                                h.detail()
                        ))
                        .toList(),
                result.whatWentWell(),
                result.whatWentWrong(),
                result.actionItems(),
                result.recommendedRecoveryAction(),
                result.recommendedRecoveryReason(),
                result.recoveryTargetVersion(),
                result.recoveryRequiresHumanApproval(),
                result.limitations(),
                result.markdownBody()
        );
    }
}
