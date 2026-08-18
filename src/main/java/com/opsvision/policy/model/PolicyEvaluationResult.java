package com.opsvision.policy.model;

import java.util.List;

/**
 * Result of deterministic deployment policy evaluation.
 *
 * @param decision gate outcome
 * @param reasons  human-readable explanations (at least one)
 * @param score    confidence score considered (0–100), if known
 */
public record PolicyEvaluationResult(
        PolicyDecision decision,
        List<String> reasons,
        Integer score
) {
    public PolicyEvaluationResult {
        if (decision == null) {
            throw new IllegalArgumentException("decision is required");
        }
        reasons = reasons == null || reasons.isEmpty()
                ? List.of("No policy reasons provided")
                : List.copyOf(reasons);
    }

    public static PolicyEvaluationResult of(PolicyDecision decision, List<String> reasons, int score) {
        return new PolicyEvaluationResult(decision, reasons, score);
    }
}
