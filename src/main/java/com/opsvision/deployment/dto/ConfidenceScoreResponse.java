package com.opsvision.deployment.dto;

import java.util.List;

public record ConfidenceScoreResponse(
        Long deploymentId,
        int score,
        List<ScoreFactorResponse> factors
) {
}
