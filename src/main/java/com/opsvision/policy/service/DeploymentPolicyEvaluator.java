package com.opsvision.policy.service;

import com.opsvision.policy.config.PolicyProperties;
import com.opsvision.policy.model.PolicyDecision;
import com.opsvision.policy.model.PolicyEvaluationInput;
import com.opsvision.policy.model.PolicyEvaluationResult;
import com.opsvision.scoring.model.ConfidenceScoreResult;
import com.opsvision.scoring.model.ScoringEvidenceItem;
import com.opsvision.scoring.model.ScoringFindingItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic deployment policy engine.
 * <p>
 * Hard overrides (critical findings, failed CI) take precedence over score bands.
 * Never uses an LLM.
 */
@Service
public class DeploymentPolicyEvaluator {

    private final PolicyProperties properties;

    public DeploymentPolicyEvaluator(PolicyProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public PolicyEvaluationResult evaluate(PolicyEvaluationInput input) {
        Objects.requireNonNull(input, "input");
        validateThresholds();

        List<String> blockReasons = new ArrayList<>();
        List<String> reviewReasons = new ArrayList<>();

        int score = input.confidenceScore();

        if (properties.isBlockOnCriticalFinding() && input.hasCriticalFinding()) {
            blockReasons.add("Critical security or analysis finding detected");
        }
        if (properties.isBlockOnFailedBuild() && input.hasFailedBuild()) {
            blockReasons.add("CI build failed");
        }
        if (properties.isBlockOnFailedTests() && input.hasFailedTests()) {
            blockReasons.add("Automated tests failed");
        }
        if (properties.isBlockOnFailedWorkflow() && input.hasFailedWorkflow()) {
            blockReasons.add("Workflow / pipeline failed");
        }
        if (score < properties.getReviewMinScore()) {
            blockReasons.add(
                    "Deployment confidence score is below minimum threshold ("
                            + score + " < " + properties.getReviewMinScore() + ")"
            );
        }

        if (!blockReasons.isEmpty()) {
            return PolicyEvaluationResult.of(PolicyDecision.BLOCK, blockReasons, score);
        }

        if (score < properties.getDeployMinScore()) {
            reviewReasons.add(
                    "Deployment confidence score requires review ("
                            + score + " in ["
                            + properties.getReviewMinScore() + ", "
                            + (properties.getDeployMinScore() - 1) + "])"
            );
        }
        if (properties.isReviewOnHighFinding() && input.hasHighFinding()) {
            reviewReasons.add("High severity finding requires human review");
        }

        if (!reviewReasons.isEmpty()) {
            return PolicyEvaluationResult.of(PolicyDecision.REVIEW, reviewReasons, score);
        }

        return PolicyEvaluationResult.of(
                PolicyDecision.DEPLOY,
                List.of(
                        "Deployment confidence score meets deploy threshold ("
                                + score + " >= " + properties.getDeployMinScore() + ")"
                                + " and no blocking or review overrides apply"
                ),
                score
        );
    }

    public PolicyEvaluationResult evaluate(ConfidenceScoreResult scoreResult) {
        return evaluate(PolicyEvaluationInput.fromScore(scoreResult));
    }

    public PolicyEvaluationResult evaluate(
            ConfidenceScoreResult scoreResult,
            Collection<ScoringEvidenceItem> evidence,
            Collection<ScoringFindingItem> additionalFindings
    ) {
        return evaluate(PolicyEvaluationInput.of(scoreResult, evidence, additionalFindings));
    }

    private void validateThresholds() {
        int deploy = properties.getDeployMinScore();
        int review = properties.getReviewMinScore();
        if (review < 0 || deploy < 0 || review > 100 || deploy > 100) {
            throw new IllegalStateException(
                    "Policy score thresholds must be between 0 and 100 (reviewMinScore="
                            + review + ", deployMinScore=" + deploy + ")"
            );
        }
        if (review > deploy) {
            throw new IllegalStateException(
                    "reviewMinScore (" + review + ") must be <= deployMinScore (" + deploy + ")"
            );
        }
    }
}
