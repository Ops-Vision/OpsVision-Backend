package com.opsvision.scoring.model;

import java.util.List;

/**
 * Deterministic deployment confidence score (0–100) with factor breakdown.
 */
public record ConfidenceScoreResult(
        int score,
        List<ScoreFactor> factors
) {
    public ConfidenceScoreResult {
        if (score < 0) {
            score = 0;
        }
        if (score > 100) {
            score = 100;
        }
        factors = factors == null ? List.of() : List.copyOf(factors);
    }
}
