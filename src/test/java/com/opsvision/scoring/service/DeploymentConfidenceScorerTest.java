package com.opsvision.scoring.service;

import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;
import com.opsvision.scoring.config.ScoringProperties;
import com.opsvision.scoring.model.ConfidenceScoreResult;
import com.opsvision.scoring.model.ScoreFactor;
import com.opsvision.scoring.model.ScoringEvidenceItem;
import com.opsvision.scoring.model.ScoringFindingItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentConfidenceScorerTest {

    private DeploymentConfidenceScorer scorer;
    private ScoringProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ScoringProperties();
        scorer = new DeploymentConfidenceScorer(properties);
    }

    @Test
    void idealDeploymentScoresOneHundred() {
        List<ScoringEvidenceItem> evidence = List.of(
                ScoringEvidenceItem.of(EvidenceType.BUILD, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.TEST, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.CODE_COVERAGE, EvidenceStatus.PASSED,
                        new BigDecimal("92.5"), List.of()),
                ScoringEvidenceItem.of(EvidenceType.STATIC_ANALYSIS, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.DEPENDENCY_SCAN, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.CONTAINER_SCAN, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.WORKFLOW, EvidenceStatus.PASSED)
        );

        ConfidenceScoreResult result = scorer.score(evidence);

        assertEquals(100, result.score());
        assertEquals(6, result.factors().size());
        Map<String, ScoreFactor> byName = byName(result);
        assertEquals(20, byName.get(DeploymentConfidenceScorer.FACTOR_BUILD).score());
        assertEquals(20, byName.get(DeploymentConfidenceScorer.FACTOR_TESTS).score());
        assertEquals(15, byName.get(DeploymentConfidenceScorer.FACTOR_COVERAGE).score());
        assertEquals(15, byName.get(DeploymentConfidenceScorer.FACTOR_STATIC_ANALYSIS).score());
        assertEquals(25, byName.get(DeploymentConfidenceScorer.FACTOR_SECURITY).score());
        assertEquals(5, byName.get(DeploymentConfidenceScorer.FACTOR_WORKFLOW).score());
        assertTrue(byName.get(DeploymentConfidenceScorer.FACTOR_TESTS).reason().toLowerCase().contains("pass"));
    }

    @Test
    void failedBuildZeroesBuildFactor() {
        List<ScoringEvidenceItem> evidence = idealBase();
        evidence = replace(evidence, EvidenceType.BUILD, EvidenceStatus.FAILED);

        ConfidenceScoreResult result = scorer.score(evidence);

        Map<String, ScoreFactor> byName = byName(result);
        assertEquals(0, byName.get(DeploymentConfidenceScorer.FACTOR_BUILD).score());
        assertEquals(80, result.score());
        assertTrue(byName.get(DeploymentConfidenceScorer.FACTOR_BUILD).reason().toLowerCase().contains("fail"));
    }

    @Test
    void poorCoverageAwardsZeroCoveragePoints() {
        List<ScoringEvidenceItem> evidence = List.of(
                ScoringEvidenceItem.of(EvidenceType.BUILD, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.TEST, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.CODE_COVERAGE, EvidenceStatus.WARNING,
                        new BigDecimal("25.0"), List.of()),
                ScoringEvidenceItem.of(EvidenceType.STATIC_ANALYSIS, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.CONTAINER_SCAN, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.WORKFLOW, EvidenceStatus.PASSED)
        );

        ConfidenceScoreResult result = scorer.score(evidence);

        Map<String, ScoreFactor> byName = byName(result);
        assertEquals(0, byName.get(DeploymentConfidenceScorer.FACTOR_COVERAGE).score());
        assertEquals(85, result.score());
        assertTrue(byName.get(DeploymentConfidenceScorer.FACTOR_COVERAGE).reason().toLowerCase().contains("coverage"));
    }

    @Test
    void partialCoverageAwardsProportionalPoints() {
        // midpoint between 40 and 80 => 50% of 15 = 7.5 -> 8
        ScoringEvidenceItem coverage = ScoringEvidenceItem.of(
                EvidenceType.CODE_COVERAGE,
                EvidenceStatus.WARNING,
                new BigDecimal("60.0"),
                List.of()
        );
        List<ScoringEvidenceItem> evidence = List.of(
                ScoringEvidenceItem.of(EvidenceType.BUILD, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.TEST, EvidenceStatus.PASSED),
                coverage,
                ScoringEvidenceItem.of(EvidenceType.STATIC_ANALYSIS, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.DEPENDENCY_SCAN, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.WORKFLOW, EvidenceStatus.PASSED)
        );

        ConfidenceScoreResult result = scorer.score(evidence);

        assertEquals(8, byName(result).get(DeploymentConfidenceScorer.FACTOR_COVERAGE).score());
        assertEquals(93, result.score());
    }

    @Test
    void criticalSecurityFindingZeroesSecurityFactor() {
        List<ScoringFindingItem> vulns = List.of(
                ScoringFindingItem.of(FindingType.CONTAINER, FindingSeverity.CRITICAL)
        );
        List<ScoringEvidenceItem> evidence = List.of(
                ScoringEvidenceItem.of(EvidenceType.BUILD, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.TEST, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.CODE_COVERAGE, EvidenceStatus.PASSED,
                        new BigDecimal("90"), List.of()),
                ScoringEvidenceItem.of(EvidenceType.STATIC_ANALYSIS, EvidenceStatus.PASSED),
                new ScoringEvidenceItem(
                        EvidenceType.CONTAINER_SCAN,
                        EvidenceStatus.FAILED,
                        "trivy",
                        "critical vuln",
                        null,
                        null,
                        vulns
                ),
                ScoringEvidenceItem.of(EvidenceType.WORKFLOW, EvidenceStatus.PASSED)
        );

        ConfidenceScoreResult result = scorer.score(evidence);

        Map<String, ScoreFactor> byName = byName(result);
        assertEquals(0, byName.get(DeploymentConfidenceScorer.FACTOR_SECURITY).score());
        assertEquals(75, result.score());
        assertTrue(byName.get(DeploymentConfidenceScorer.FACTOR_SECURITY).reason().toLowerCase().contains("critical"));
    }

    @Test
    void highSecurityFindingsApplyPenalty() {
        // 2 high * 10 = 20 penalty => security 5/25
        List<ScoringFindingItem> vulns = List.of(
                ScoringFindingItem.of(FindingType.DEPENDENCY, FindingSeverity.HIGH),
                ScoringFindingItem.of(FindingType.DEPENDENCY, FindingSeverity.HIGH)
        );
        List<ScoringEvidenceItem> evidence = List.of(
                ScoringEvidenceItem.of(EvidenceType.BUILD, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.TEST, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.CODE_COVERAGE, EvidenceStatus.PASSED,
                        new BigDecimal("85"), List.of()),
                ScoringEvidenceItem.of(EvidenceType.STATIC_ANALYSIS, EvidenceStatus.PASSED),
                new ScoringEvidenceItem(
                        EvidenceType.DEPENDENCY_SCAN,
                        EvidenceStatus.WARNING,
                        "trivy",
                        null,
                        null,
                        null,
                        vulns
                ),
                ScoringEvidenceItem.of(EvidenceType.WORKFLOW, EvidenceStatus.PASSED)
        );

        ConfidenceScoreResult result = scorer.score(evidence);

        assertEquals(5, byName(result).get(DeploymentConfidenceScorer.FACTOR_SECURITY).score());
        assertEquals(80, result.score());
    }

    @Test
    void mixedEvidenceProducesExplainableBreakdown() {
        List<ScoringEvidenceItem> evidence = List.of(
                ScoringEvidenceItem.of(EvidenceType.BUILD, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.TEST, EvidenceStatus.FAILED),
                ScoringEvidenceItem.of(EvidenceType.CODE_COVERAGE, EvidenceStatus.WARNING,
                        new BigDecimal("60.0"), List.of()),
                new ScoringEvidenceItem(
                        EvidenceType.STATIC_ANALYSIS,
                        EvidenceStatus.WARNING,
                        "semgrep",
                        null,
                        null,
                        null,
                        List.of(ScoringFindingItem.of(FindingType.STATIC_ANALYSIS, FindingSeverity.HIGH))
                ),
                new ScoringEvidenceItem(
                        EvidenceType.CONTAINER_SCAN,
                        EvidenceStatus.WARNING,
                        "trivy",
                        null,
                        null,
                        null,
                        List.of(ScoringFindingItem.of(FindingType.CONTAINER, FindingSeverity.MEDIUM))
                ),
                ScoringEvidenceItem.of(EvidenceType.WORKFLOW, EvidenceStatus.WARNING)
        );

        ConfidenceScoreResult result = scorer.score(evidence);
        Map<String, ScoreFactor> byName = byName(result);

        // build 20 + tests 0 + coverage 8 + static 15-8=7 + security 25-4=21 + workflow 2 = 58
        assertEquals(20, byName.get(DeploymentConfidenceScorer.FACTOR_BUILD).score());
        assertEquals(0, byName.get(DeploymentConfidenceScorer.FACTOR_TESTS).score());
        assertEquals(8, byName.get(DeploymentConfidenceScorer.FACTOR_COVERAGE).score());
        assertEquals(7, byName.get(DeploymentConfidenceScorer.FACTOR_STATIC_ANALYSIS).score());
        assertEquals(21, byName.get(DeploymentConfidenceScorer.FACTOR_SECURITY).score());
        assertEquals(2, byName.get(DeploymentConfidenceScorer.FACTOR_WORKFLOW).score());
        assertEquals(58, result.score());

        result.factors().forEach(f -> {
            assertTrue(f.maxScore() >= f.score());
            assertTrue(f.reason() != null && !f.reason().isBlank());
        });
    }

    @Test
    void missingEvidenceScoresZeroByDefault() {
        ConfidenceScoreResult result = scorer.score(List.of());

        assertEquals(0, result.score());
        assertEquals(6, result.factors().size());
        result.factors().forEach(f -> {
            assertEquals(0, f.score());
            assertTrue(f.reason().toLowerCase().contains("missing"));
        });
    }

    @Test
    void missingEvidenceCanReceiveConfiguredPartialCredit() {
        properties.setMissingEvidenceCredit(0.5);
        scorer = new DeploymentConfidenceScorer(properties);

        ConfidenceScoreResult result = scorer.score(List.of(
                ScoringEvidenceItem.of(EvidenceType.BUILD, EvidenceStatus.PASSED)
        ));

        Map<String, ScoreFactor> byName = byName(result);
        assertEquals(20, byName.get(DeploymentConfidenceScorer.FACTOR_BUILD).score());
        assertEquals(10, byName.get(DeploymentConfidenceScorer.FACTOR_TESTS).score());
        // Half maxima via Math.round: 15*0.5=7.5->8, 25*0.5=12.5->13, 5*0.5=2.5->3
        assertEquals(8, byName.get(DeploymentConfidenceScorer.FACTOR_COVERAGE).score());
        assertEquals(8, byName.get(DeploymentConfidenceScorer.FACTOR_STATIC_ANALYSIS).score());
        assertEquals(13, byName.get(DeploymentConfidenceScorer.FACTOR_SECURITY).score());
        assertEquals(3, byName.get(DeploymentConfidenceScorer.FACTOR_WORKFLOW).score());
        assertEquals(20 + 10 + 8 + 8 + 13 + 3, result.score());
    }

    @Test
    void scoringIsDeterministicAcrossCalls() {
        List<ScoringEvidenceItem> evidence = mixedSample();
        ConfidenceScoreResult a = scorer.score(evidence);
        ConfidenceScoreResult b = scorer.score(evidence);
        assertEquals(a, b);
        assertEquals(a.score(), b.score());
    }

    @Test
    void additionalFindingsFeedSecurityFactor() {
        List<ScoringEvidenceItem> evidence = List.of(
                ScoringEvidenceItem.of(EvidenceType.BUILD, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.TEST, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.CODE_COVERAGE, EvidenceStatus.PASSED,
                        new BigDecimal("90"), List.of()),
                ScoringEvidenceItem.of(EvidenceType.STATIC_ANALYSIS, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.CONTAINER_SCAN, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.WORKFLOW, EvidenceStatus.PASSED)
        );
        List<ScoringFindingItem> extra = List.of(
                ScoringFindingItem.of(FindingType.SECURITY_VULNERABILITY, FindingSeverity.CRITICAL)
        );

        ConfidenceScoreResult result = scorer.score(evidence, extra);
        assertEquals(0, byName(result).get(DeploymentConfidenceScorer.FACTOR_SECURITY).score());
        assertEquals(75, result.score());
    }

    private static List<ScoringEvidenceItem> idealBase() {
        return List.of(
                ScoringEvidenceItem.of(EvidenceType.BUILD, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.TEST, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.CODE_COVERAGE, EvidenceStatus.PASSED,
                        new BigDecimal("90"), List.of()),
                ScoringEvidenceItem.of(EvidenceType.STATIC_ANALYSIS, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.DEPENDENCY_SCAN, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.WORKFLOW, EvidenceStatus.PASSED)
        );
    }

    private static List<ScoringEvidenceItem> mixedSample() {
        return List.of(
                ScoringEvidenceItem.of(EvidenceType.BUILD, EvidenceStatus.PASSED),
                ScoringEvidenceItem.of(EvidenceType.TEST, EvidenceStatus.WARNING),
                ScoringEvidenceItem.of(EvidenceType.CODE_COVERAGE, EvidenceStatus.PASSED,
                        new BigDecimal("70"), List.of())
        );
    }

    private static List<ScoringEvidenceItem> replace(
            List<ScoringEvidenceItem> source,
            EvidenceType type,
            EvidenceStatus status
    ) {
        return source.stream()
                .map(e -> e.type() == type ? ScoringEvidenceItem.of(type, status) : e)
                .toList();
    }

    private static Map<String, ScoreFactor> byName(ConfidenceScoreResult result) {
        return result.factors().stream()
                .collect(Collectors.toMap(ScoreFactor::name, Function.identity()));
    }
}
