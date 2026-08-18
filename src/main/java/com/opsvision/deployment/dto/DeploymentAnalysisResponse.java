package com.opsvision.deployment.dto;

import java.util.List;

/**
 * Full analysis payload: deployment metadata, evidence, findings, score, and policy.
 */
public record DeploymentAnalysisResponse(
        DeploymentResponse deployment,
        List<EvidenceResponse> evidence,
        List<FindingResponse> findings,
        ConfidenceScoreResponse score,
        PolicyDecisionResponse policy
) {
}
