package com.opsvision.integration;

import com.opsvision.deployment.dto.AnalyzeDeploymentRequest;
import com.opsvision.deployment.dto.DeploymentAnalysisResponse;
import com.opsvision.deployment.dto.PolicyDecisionResponse;
import com.opsvision.deployment.service.DeploymentAnalysisService;
import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import com.opsvision.evidence.dto.NormalizedFindingInput;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;
import com.opsvision.policy.model.PolicyDecision;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critical path: CI evidence → deployment analysis → confidence score → policy decision.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
@Transactional
class DeploymentAnalysisWorkflowIntegrationTest {

    @Autowired
    private DeploymentAnalysisService analysisService;

    @Test
    void idealEvidence_scoresHighAndDeploys() {
        AnalyzeDeploymentRequest request = baseRequest(
                "abc111ideal00000000000000000000000000001",
                List.of(
                        evidence(EvidenceType.BUILD, EvidenceStatus.PASSED, "ci", "Build OK"),
                        evidence(EvidenceType.TEST, EvidenceStatus.PASSED, "ci", "Tests OK"),
                        evidence(EvidenceType.WORKFLOW, EvidenceStatus.PASSED, "github-actions", "Workflow OK"),
                        coverage(EvidenceStatus.PASSED, 92.0),
                        evidence(EvidenceType.STATIC_ANALYSIS, EvidenceStatus.PASSED, "semgrep", "Clean"),
                        evidence(EvidenceType.CONTAINER_SCAN, EvidenceStatus.PASSED, "trivy", "Clean")
                )
        );

        DeploymentAnalysisResponse analysis = analysisService.analyze(request);

        assertThat(analysis.deployment().id()).isNotNull();
        assertThat(analysis.evidence()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(analysis.score().score()).isGreaterThanOrEqualTo(80);
        assertThat(analysis.policy().decision()).isEqualTo(PolicyDecision.DEPLOY);

        PolicyDecisionResponse policy = analysisService.getPolicy(analysis.deployment().id());
        assertThat(policy.decision()).isEqualTo(PolicyDecision.DEPLOY);
        assertThat(analysisService.getScore(analysis.deployment().id()).score())
                .isEqualTo(analysis.score().score());
    }

    @Test
    void failedBuildAndCriticalFinding_blocksRegardlessOfOtherEvidence() {
        AnalyzeDeploymentRequest request = baseRequest(
                "abc222block00000000000000000000000000002",
                List.of(
                        evidence(EvidenceType.BUILD, EvidenceStatus.FAILED, "ci", "Build failed"),
                        evidence(EvidenceType.TEST, EvidenceStatus.PASSED, "ci", "Tests OK"),
                        coverage(EvidenceStatus.WARNING, 55.0),
                        new NormalizedEvidenceInput(
                                EvidenceType.CONTAINER_SCAN,
                                EvidenceStatus.FAILED,
                                "trivy",
                                "Critical CVE",
                                null,
                                null,
                                null,
                                Instant.parse("2026-01-01T12:00:00Z"),
                                List.of(new NormalizedFindingInput(
                                        FindingType.CONTAINER,
                                        FindingSeverity.CRITICAL,
                                        "CVE-2024-0001",
                                        "Critical container vulnerability",
                                        "RCE in base image",
                                        null,
                                        null,
                                        "openssl",
                                        "1.0.0",
                                        "1.1.0",
                                        "CVE-2024-0001"
                                ))
                        )
                )
        );

        DeploymentAnalysisResponse analysis = analysisService.analyze(request);

        assertThat(analysis.score().score()).isLessThan(80);
        assertThat(analysis.findings()).isNotEmpty();
        assertThat(analysis.policy().decision()).isEqualTo(PolicyDecision.BLOCK);
        assertThat(analysis.policy().reasons()).isNotEmpty();
        assertThat(analysisService.getAnalysis(analysis.deployment().id()).policy().decision())
                .isEqualTo(PolicyDecision.BLOCK);
    }

    @Test
    void reanalyze_isDeterministicForSameStoredEvidence() {
        AnalyzeDeploymentRequest request = baseRequest(
                "abc333rean00000000000000000000000000003",
                List.of(
                        evidence(EvidenceType.BUILD, EvidenceStatus.PASSED, "ci", "ok"),
                        evidence(EvidenceType.TEST, EvidenceStatus.PASSED, "ci", "ok"),
                        coverage(EvidenceStatus.PASSED, 70.0)
                )
        );

        DeploymentAnalysisResponse first = analysisService.analyze(request);
        DeploymentAnalysisResponse second = analysisService.reanalyze(first.deployment().id());

        assertThat(second.score().score()).isEqualTo(first.score().score());
        assertThat(second.policy().decision()).isEqualTo(first.policy().decision());
        assertThat(second.score().factors()).hasSameSizeAs(first.score().factors());
    }

    private static AnalyzeDeploymentRequest baseRequest(String sha, List<NormalizedEvidenceInput> evidence) {
        return new AnalyzeDeploymentRequest(
                "opsvision",
                "demo-app",
                sha,
                "main",
                "staging",
                "ci.yml",
                1001L,
                "https://github.com/opsvision/demo-app/actions/runs/1001",
                "https://github.com/opsvision/demo-app",
                evidence
        );
    }

    private static NormalizedEvidenceInput evidence(
            EvidenceType type,
            EvidenceStatus status,
            String source,
            String summary
    ) {
        return NormalizedEvidenceInput.builder(type, status, source)
                .summary(summary)
                .collectedAt(Instant.parse("2026-01-01T12:00:00Z"))
                .build();
    }

    private static NormalizedEvidenceInput coverage(EvidenceStatus status, double percent) {
        return NormalizedEvidenceInput.builder(EvidenceType.CODE_COVERAGE, status, "jacoco")
                .summary("Coverage " + percent + "%")
                .metricValue(BigDecimal.valueOf(percent))
                .metricUnit("percent")
                .collectedAt(Instant.parse("2026-01-01T12:00:00Z"))
                .build();
    }
}
