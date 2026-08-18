package com.opsvision.deployment.dto;

public record ScoreFactorResponse(
        String name,
        int score,
        int maxScore,
        String reason
) {
}
