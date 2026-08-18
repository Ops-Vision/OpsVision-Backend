package com.opsvision.policy.service;

import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.policy.config.PolicyProperties;
import com.opsvision.policy.model.PolicyDecision;
import com.opsvision.policy.model.PolicyEvaluationInput;
import com.opsvision.policy.model.PolicyEvaluationResult;
import com.opsvision.scoring.model.ConfidenceScoreResult;
import com.opsvision.scoring.model.ScoreFactor;
import com.opsvision.scoring.model.ScoringEvidenceItem;
import com.opsvision.scoring.model.ScoringFindingItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentPolicyEvaluatorTest {

    private PolicyProperties properties;
    private DeploymentPolicyEvaluator evaluator;

    @BeforeEach
    void setUp() {
        properties = new PolicyProperties();
        evaluator = new DeploymentPolicyEvaluator(properties);
    }

    @Test
    void scoreAtDeployThresholdIsDeploy() {
        PolicyEvaluationResult result = evaluator.evaluate(scoreOnly(80));

        assertEquals(PolicyDecision.DEPLOY, result.decision());
        assertEquals(80, result.score());
        assertFalse(result.reasons().isEmpty());
        assertTrue(result.reasons().getFirst().toLowerCase().contains("deploy"));
    }

    @Test
    void scoreJustBelowDeployIsReview() {
        PolicyEvaluationResult result = evaluator.evaluate(scoreOnly(79));

        assertEquals(PolicyDecision.REVIEW, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("requires review")));
    }

    @Test
    void scoreAtReviewThresholdIsReview() {
        assertEquals(PolicyDecision.REVIEW, evaluator.evaluate(scoreOnly(60)).decision());
    }

    @Test
    void scoreJustBelowReviewIsBlock() {
        PolicyEvaluationResult result = evaluator.evaluate(scoreOnly(59));

        assertEquals(PolicyDecision.BLOCK, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains("below minimum threshold")));
    }

    @Test
    void perfectScoreIsDeploy() {
        assertEquals(PolicyDecision.DEPLOY, evaluator.evaluate(scoreOnly(100)).decision());
    }

    @Test
    void zeroScoreIsBlock() {
        assertEquals(PolicyDecision.BLOCK, evaluator.evaluate(scoreOnly(0)).decision());
    }

    @Test
    void criticalFindingOverridesHighScore() {
        PolicyEvaluationInput input = new PolicyEvaluationInput(
                95,
                Map.of(),
                List.of(FindingSeverity.CRITICAL)
        );

        PolicyEvaluationResult result = evaluator.evaluate(input);

        assertEquals(PolicyDecision.BLOCK, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.toLowerCase().contains("critical")));
    }

    @Test
    void failedBuildOverridesHighScore() {
        Map<EvidenceType, EvidenceStatus> statuses = new EnumMap<>(EvidenceType.class);
        statuses.put(EvidenceType.BUILD, EvidenceStatus.FAILED);

        PolicyEvaluationResult result = evaluator.evaluate(
                new PolicyEvaluationInput(90, statuses, List.of())
        );

        assertEquals(PolicyDecision.BLOCK, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.toLowerCase().contains("build")));
    }

    @Test
    void failedTestsOverrideHighScore() {
        Map<EvidenceType, EvidenceStatus> statuses = new EnumMap<>(EvidenceType.class);
        statuses.put(EvidenceType.TEST, EvidenceStatus.FAILED);

        PolicyEvaluationResult result = evaluator.evaluate(
                new PolicyEvaluationInput(88, statuses, List.of())
        );

        assertEquals(PolicyDecision.BLOCK, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.toLowerCase().contains("test")));
    }

    @Test
    void failedWorkflowOverridesHighScore() {
        Map<EvidenceType, EvidenceStatus> statuses = new EnumMap<>(EvidenceType.class);
        statuses.put(EvidenceType.WORKFLOW, EvidenceStatus.FAILED);

        PolicyEvaluationResult result = evaluator.evaluate(
                new PolicyEvaluationInput(85, statuses, List.of())
        );

        assertEquals(PolicyDecision.BLOCK, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.toLowerCase().contains("workflow")));
    }

    @Test
    void highFindingForcesReviewEvenWhenScoreAllowsDeploy() {
        PolicyEvaluationResult result = evaluator.evaluate(
                new PolicyEvaluationInput(90, Map.of(), List.of(FindingSeverity.HIGH))
        );

        assertEquals(PolicyDecision.REVIEW, result.decision());
        assertTrue(result.reasons().stream().anyMatch(r -> r.toLowerCase().contains("high")));
    }

    @Test
    void highFindingWithLowScoreStillBlocks() {
        PolicyEvaluationResult result = evaluator.evaluate(
                new PolicyEvaluationInput(50, Map.of(), List.of(FindingSeverity.HIGH))
        );

        assertEquals(PolicyDecision.BLOCK, result.decision());
    }

    @Test
    void multipleBlockReasonsAreCollected() {
        Map<EvidenceType, EvidenceStatus> statuses = new EnumMap<>(EvidenceType.class);
        statuses.put(EvidenceType.BUILD, EvidenceStatus.FAILED);
        statuses.put(EvidenceType.TEST, EvidenceStatus.FAILED);

        PolicyEvaluationResult result = evaluator.evaluate(
                new PolicyEvaluationInput(
                        40,
                        statuses,
                        List.of(FindingSeverity.CRITICAL)
                )
        );

        assertEquals(PolicyDecision.BLOCK, result.decision());
        assertTrue(result.reasons().size() >= 3);
    }

    @Test
    void evaluateFromEvidenceAndFindingsBuildsInput() {
        ConfidenceScoreResult score = new ConfidenceScoreResult(92, List.of(
                new ScoreFactor("build", 20, 20, "ok")
        ));
        List<ScoringEvidenceItem> evidence = List.of(
                ScoringEvidenceItem.of(EvidenceType.BUILD, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(
                        EvidenceType.CONTAINER_SCAN,
                        EvidenceStatus.WARNING,
                        null,
                        List.of(ScoringFindingItem.of(FindingSeverity.HIGH))
                )
        );

        PolicyEvaluationResult result = evaluator.evaluate(score, evidence, List.of());

        assertEquals(PolicyDecision.REVIEW, result.decision());
        assertEquals(92, result.score());
    }

    @Test
    void criticalOverrideCanBeDisabled() {
        properties.setBlockOnCriticalFinding(false);
        evaluator = new DeploymentPolicyEvaluator(properties);

        PolicyEvaluationResult result = evaluator.evaluate(
                new PolicyEvaluationInput(85, Map.of(), List.of(FindingSeverity.CRITICAL))
        );

        assertEquals(PolicyDecision.DEPLOY, result.decision());
    }

    @Test
    void customThresholdsAreRespected() {
        properties.setDeployMinScore(90);
        properties.setReviewMinScore(70);
        evaluator = new DeploymentPolicyEvaluator(properties);

        assertEquals(PolicyDecision.REVIEW, evaluator.evaluate(scoreOnly(85)).decision());
        assertEquals(PolicyDecision.DEPLOY, evaluator.evaluate(scoreOnly(90)).decision());
        assertEquals(PolicyDecision.BLOCK, evaluator.evaluate(scoreOnly(69)).decision());
    }

    @Test
    void invalidThresholdsThrow() {
        properties.setReviewMinScore(90);
        properties.setDeployMinScore(70);
        evaluator = new DeploymentPolicyEvaluator(properties);

        assertThrows(IllegalStateException.class, () -> evaluator.evaluate(scoreOnly(80)));
    }

    @Test
    void mediumFindingsDoNotOverrideDeploy() {
        PolicyEvaluationResult result = evaluator.evaluate(
                new PolicyEvaluationInput(82, Map.of(), List.of(FindingSeverity.MEDIUM, FindingSeverity.LOW))
        );

        assertEquals(PolicyDecision.DEPLOY, result.decision());
    }

    private static ConfidenceScoreResult scoreOnly(int score) {
        return new ConfidenceScoreResult(score, List.of());
    }
}
