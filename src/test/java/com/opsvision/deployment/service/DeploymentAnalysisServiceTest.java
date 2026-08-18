package com.opsvision.deployment.service;

import com.opsvision.deployment.dto.AnalyzeDeploymentRequest;
import com.opsvision.deployment.dto.DeploymentAnalysisResponse;
import com.opsvision.deployment.dto.DeploymentResponse;
import com.opsvision.deployment.model.DeploymentStatus;
import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import com.opsvision.evidence.dto.NormalizedFindingInput;
import com.opsvision.evidence.exception.DeploymentNotFoundException;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;
import com.opsvision.policy.model.PolicyDecision;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class DeploymentAnalysisServiceTest {

    @Autowired
    private DeploymentAnalysisService analysisService;

    @Test
    void analyzeIngestsEvidenceScoresAndReturnsDeployPolicyForHealthyPipeline() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AnalyzeDeploymentRequest request = new AnalyzeDeploymentRequest(
                "acme",
                "payments-" + suffix,
                "deadbeef" + suffix,
                "main",
                "staging",
                "CI",
                42L,
                "https://github.com/acme/payments/actions/42",
                null,
                List.of(
                        NormalizedEvidenceInput.builder(EvidenceType.BUILD, EvidenceStatus.PASSED, "ci")
                                .summary("Build OK").build(),
                        NormalizedEvidenceInput.builder(EvidenceType.TEST, EvidenceStatus.PASSED, "junit")
                                .summary("Tests OK").build(),
                        NormalizedEvidenceInput.builder(EvidenceType.CODE_COVERAGE, EvidenceStatus.PASSED, "jacoco")
                                .summary("Coverage")
                                .metricValue(new BigDecimal("90.0"))
                                .metricUnit("percent")
                                .build(),
                        NormalizedEvidenceInput.builder(EvidenceType.STATIC_ANALYSIS, EvidenceStatus.PASSED, "semgrep")
                                .summary("Clean").build(),
                        NormalizedEvidenceInput.builder(EvidenceType.CONTAINER_SCAN, EvidenceStatus.PASSED, "trivy")
                                .summary("Clean").build(),
                        NormalizedEvidenceInput.builder(EvidenceType.WORKFLOW, EvidenceStatus.PASSED, "gha")
                                .summary("Workflow OK").build()
                )
        );

        DeploymentAnalysisResponse result = analysisService.analyze(request);

        assertThat(result.deployment().id()).isNotNull();
        assertThat(result.deployment().status()).isEqualTo(DeploymentStatus.SUCCEEDED);
        assertThat(result.evidence()).hasSize(6);
        assertThat(result.score().score()).isEqualTo(100);
        assertThat(result.policy().decision()).isEqualTo(PolicyDecision.DEPLOY);

        Long id = result.deployment().id();
        assertThat(analysisService.getScore(id).score()).isEqualTo(100);
        assertThat(analysisService.getPolicy(id).decision()).isEqualTo(PolicyDecision.DEPLOY);
        assertThat(analysisService.listEvidence(id)).hasSize(6);
        assertThat(analysisService.listFindings(id)).isEmpty();
    }

    @Test
    void analyzeBlocksOnCriticalFinding() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AnalyzeDeploymentRequest request = new AnalyzeDeploymentRequest(
                "acme",
                "sec-" + suffix,
                "cafebabe" + suffix,
                "main",
                "prod",
                null,
                null,
                null,
                null,
                List.of(
                        NormalizedEvidenceInput.builder(EvidenceType.BUILD, EvidenceStatus.PASSED, "ci")
                                .summary("ok").build(),
                        NormalizedEvidenceInput.builder(EvidenceType.TEST, EvidenceStatus.PASSED, "junit")
                                .summary("ok").build(),
                        NormalizedEvidenceInput.builder(EvidenceType.CONTAINER_SCAN, EvidenceStatus.FAILED, "trivy")
                                .summary("vuln")
                                .findings(List.of(new NormalizedFindingInput(
                                        FindingType.CONTAINER,
                                        FindingSeverity.CRITICAL,
                                        "CVE-1",
                                        "Critical CVE",
                                        "RCE",
                                        null,
                                        null,
                                        "openssl",
                                        "1.0",
                                        "1.1",
                                        "CVE-1"
                                )))
                                .build()
                )
        );

        DeploymentAnalysisResponse result = analysisService.analyze(request);

        assertThat(result.findings()).hasSize(1);
        assertThat(result.policy().decision()).isEqualTo(PolicyDecision.BLOCK);
        assertThat(result.policy().reasons()).anyMatch(r -> r.toLowerCase().contains("critical"));
    }

    @Test
    void listRecentAndGetDeployment() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        DeploymentAnalysisResponse created = analysisService.analyze(new AnalyzeDeploymentRequest(
                "org",
                "app-" + suffix,
                "sha-" + suffix,
                "develop",
                "dev",
                null, null, null, null,
                List.of()
        ));

        Page<DeploymentResponse> page = analysisService.listRecent(0, 10);
        assertThat(page.getContent()).extracting(DeploymentResponse::id)
                .contains(created.deployment().id());

        DeploymentResponse loaded = analysisService.getDeployment(created.deployment().id());
        assertThat(loaded.repository().name()).isEqualTo("app-" + suffix);
        assertThat(loaded.commitSha()).isEqualTo("sha-" + suffix);
    }

    @Test
    void missingDeploymentThrows() {
        assertThatThrownBy(() -> analysisService.getDeployment(9_999_999L))
                .isInstanceOf(DeploymentNotFoundException.class);
    }
}
