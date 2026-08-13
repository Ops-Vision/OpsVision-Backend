package com.opsvision.deployment.repository;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.entity.ProjectRepository;
import com.opsvision.deployment.model.DeploymentStatus;
import com.opsvision.evidence.entity.DeploymentEvidence;
import com.opsvision.evidence.entity.Finding;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;
import com.opsvision.evidence.repository.DeploymentEvidenceRepository;
import com.opsvision.evidence.repository.FindingRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class DeploymentDomainPersistenceTest {

    @Autowired
    private ProjectRepositoryRepository projectRepositoryRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private DeploymentEvidenceRepository deploymentEvidenceRepository;

    @Autowired
    private FindingRepository findingRepository;

    @Test
    void persistsRepositoryDeploymentEvidenceAndFindings() {
        ProjectRepository repo = projectRepositoryRepository.save(
                new ProjectRepository("acme", "payments", "main", "https://github.com/acme/payments")
        );

        assertThat(repo.getId()).isNotNull();
        assertThat(repo.getFullName()).isEqualTo("acme/payments");
        assertThat(repo.getCreatedAt()).isNotNull();

        Deployment deployment = new Deployment(
                repo,
                "abc123def456",
                "main",
                "staging",
                DeploymentStatus.ANALYZING
        );
        deployment.setWorkflowName("ci.yml");
        deployment.setWorkflowRunId(42L);
        deployment.setWorkflowRunUrl("https://github.com/acme/payments/actions/runs/42");
        deployment.setDeployedAt(Instant.parse("2026-01-15T10:00:00Z"));

        DeploymentEvidence buildEvidence = new DeploymentEvidence(
                EvidenceType.BUILD,
                EvidenceStatus.PASSED,
                "github-actions",
                "Build completed successfully"
        );

        DeploymentEvidence coverageEvidence = new DeploymentEvidence(
                EvidenceType.CODE_COVERAGE,
                EvidenceStatus.PASSED,
                "jacoco",
                "Line coverage 87%"
        );
        coverageEvidence.setMetricValue(new BigDecimal("87.0000"));
        coverageEvidence.setMetricUnit("percent");

        DeploymentEvidence scanEvidence = new DeploymentEvidence(
                EvidenceType.CONTAINER_SCAN,
                EvidenceStatus.WARNING,
                "trivy",
                "1 high severity vulnerability"
        );

        deployment.addEvidence(buildEvidence);
        deployment.addEvidence(coverageEvidence);
        deployment.addEvidence(scanEvidence);

        Finding vuln = new Finding(
                FindingType.CONTAINER,
                FindingSeverity.HIGH,
                "CVE-2024-1234 in openssl",
                "OpenSSL vulnerability in base image"
        );
        vuln.setExternalId("CVE-2024-1234");
        vuln.setPackageName("openssl");
        vuln.setInstalledVersion("1.1.1");
        vuln.setFixedVersion("1.1.1w");
        vuln.setEvidence(scanEvidence);
        deployment.addFinding(vuln);

        Finding staticHit = new Finding(
                FindingType.STATIC_ANALYSIS,
                FindingSeverity.MEDIUM,
                "SQL injection risk",
                "User input concatenated into SQL"
        );
        staticHit.setRuleId("java.lang.security.audit.sqli");
        staticHit.setFilePath("src/main/java/com/acme/Dao.java");
        staticHit.setLineNumber(42);
        deployment.addFinding(staticHit);

        Deployment saved = deploymentRepository.saveAndFlush(deployment);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(DeploymentStatus.ANALYZING);
        assertThat(saved.getEvidenceItems()).hasSize(3);
        assertThat(saved.getFindings()).hasSize(2);

        Optional<Deployment> byKey = deploymentRepository.findByRepositoryIdAndCommitShaAndEnvironment(
                repo.getId(), "abc123def456", "staging"
        );
        assertThat(byKey).isPresent();
        assertThat(byKey.get().getWorkflowRunId()).isEqualTo(42L);

        List<DeploymentEvidence> evidence = deploymentEvidenceRepository.findByDeploymentId(saved.getId());
        assertThat(evidence).hasSize(3);
        assertThat(deploymentEvidenceRepository.findByDeploymentIdAndEvidenceType(
                saved.getId(), EvidenceType.CODE_COVERAGE
        )).singleElement().satisfies(e -> {
            assertThat(e.getMetricValue()).isEqualByComparingTo("87.0000");
            assertThat(e.getMetricUnit()).isEqualTo("percent");
        });

        List<Finding> findings = findingRepository.findByDeploymentId(saved.getId());
        assertThat(findings).hasSize(2);
        assertThat(findingRepository.countByDeploymentIdAndSeverity(saved.getId(), FindingSeverity.HIGH))
                .isEqualTo(1);
        assertThat(findingRepository.findByDeploymentIdAndSeverity(saved.getId(), FindingSeverity.MEDIUM))
                .singleElement()
                .extracting(Finding::getRuleId)
                .isEqualTo("java.lang.security.audit.sqli");

        Optional<ProjectRepository> loadedRepo =
                projectRepositoryRepository.findByOwnerAndName("acme", "payments");
        assertThat(loadedRepo).isPresent();
        assertThat(projectRepositoryRepository.existsByOwnerAndName("acme", "payments")).isTrue();
        assertThat(projectRepositoryRepository.findByFullName("acme/payments")).isPresent();
    }

    @Test
    void cascadeDeletesEvidenceAndFindingsWithDeployment() {
        ProjectRepository repo = projectRepositoryRepository.save(
                new ProjectRepository("acme", "billing", "main", null)
        );

        Deployment deployment = new Deployment(
                repo, "deadbeef", "develop", "prod", DeploymentStatus.FAILED
        );
        DeploymentEvidence evidence = new DeploymentEvidence(
                EvidenceType.TEST, EvidenceStatus.FAILED, "junit", "3 tests failed"
        );
        deployment.addEvidence(evidence);
        Finding finding = new Finding(
                FindingType.TEST_FAILURE, FindingSeverity.HIGH, "PaymentTest failed", null
        );
        deployment.addFinding(finding);

        Deployment saved = deploymentRepository.saveAndFlush(deployment);
        Long deploymentId = saved.getId();

        assertThat(deploymentEvidenceRepository.findByDeploymentId(deploymentId)).hasSize(1);
        assertThat(findingRepository.findByDeploymentId(deploymentId)).hasSize(1);

        deploymentRepository.delete(saved);
        deploymentRepository.flush();

        assertThat(deploymentRepository.findById(deploymentId)).isEmpty();
        assertThat(deploymentEvidenceRepository.findByDeploymentId(deploymentId)).isEmpty();
        assertThat(findingRepository.findByDeploymentId(deploymentId)).isEmpty();
    }
}
