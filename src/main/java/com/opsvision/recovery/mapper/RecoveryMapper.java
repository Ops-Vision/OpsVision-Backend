package com.opsvision.recovery.mapper;

import com.opsvision.recovery.dto.RecoveryRecommendationResponse;
import com.opsvision.recovery.model.RecoveryRecommendationResult;
import org.springframework.stereotype.Component;

@Component
public class RecoveryMapper {

    public RecoveryRecommendationResponse toResponse(RecoveryRecommendationResult result) {
        return new RecoveryRecommendationResponse(
                result.incidentId(),
                result.recommendedAt(),
                result.action(),
                result.reason(),
                result.targetVersion(),
                result.targetDeploymentId(),
                result.currentCommitSha(),
                result.topRcaCategory(),
                result.topRcaConfidence(),
                result.requiresHumanApproval(),
                result.executionMode(),
                result.supportingEvidence(),
                result.notes()
        );
    }
}
