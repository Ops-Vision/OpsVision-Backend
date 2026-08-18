package com.opsvision.deployment.dto;

import com.opsvision.policy.model.PolicyDecision;

import java.util.List;

public record PolicyDecisionResponse(
        Long deploymentId,
        PolicyDecision decision,
        List<String> reasons,
        Integer score
) {
}
