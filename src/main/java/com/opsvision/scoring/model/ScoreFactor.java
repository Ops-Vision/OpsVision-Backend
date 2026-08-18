package com.opsvision.scoring.model;

/**
 * Single explainable contribution toward the deployment confidence score.
 *
 * @param name     stable factor key (e.g. {@code tests}, {@code security})
 * @param score    points awarded (0–maxScore)
 * @param maxScore maximum points for this factor
 * @param reason   human-readable explanation of the award
 */
public record ScoreFactor(
        String name,
        int score,
        int maxScore,
        String reason
) {
    public ScoreFactor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("factor name must not be blank");
        }
        if (maxScore < 0) {
            throw new IllegalArgumentException("maxScore must be >= 0");
        }
        if (score < 0) {
            score = 0;
        }
        if (score > maxScore) {
            score = maxScore;
        }
        if (reason == null) {
            reason = "";
        }
    }
}
