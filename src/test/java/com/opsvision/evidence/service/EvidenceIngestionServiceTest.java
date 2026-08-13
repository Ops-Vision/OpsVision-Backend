package com.opsvision.evidence.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.entity.ProjectRepository;
import com.opsvision.deployment.model.DeploymentStatus;
import com.opsvision.deployment.repository.DeploymentRepository;
import com.opsvision.deployment.repository.ProjectRepositoryRepository;
import com.opsvision.evidence.dto.EvidenceIngestionRequest;
import com.opsvision.evidence.dto.EvidenceIngestionResult;
import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import com.opsvision.evidence.dto.NormalizedFindingInput;
import com.opsvision.evidence.entity.DeploymentEvidence;
import com.opsvision.evidence.entity.Finding;
import com.opsvision.evidence.exception.DeploymentNotFoundException;
import com.opsvision.evidence.exception.EvidenceIngestionException;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;
import com.opsvision.evidence.repository.DeploymentEvidenceRepository;
import com.opsvision.evidence.repository.FindingRepository;
import com.opsvision.github.model.GitHubWorkflowRunInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
class EvidenceIngestionServiceTest {

    @Autowired
    private EvidenceIngestionService evidenceIngestionService;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private ProjectRepositoryRepository projectRepositoryRepository;

    @Autowired
    private DeploymentEvidenceRepository deploymentEvidenceRepository;

    @Autowired
    private FindingRepository findingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Deployment deployment;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ProjectRepository repo = projectRepositoryRepository.save(
                new ProjectRepository("acme", "payments-" + suffix, "main", "https://github.com/acme/payments")
        );
        deployment = deploymentRepository.saveAndFlush(new Deployment(
                repo,
                "abc123" + suffix,
                "main",
                "staging",
                DeploymentStatus.ANALYZING
        ));
    }

    @Test
    void ingestsNormalizedBuildTestCoverageAndSecurityEvidence() {
        List<NormalizedEvidenceInput> items = List.of(
                NormalizedEvidenceInput.builder(EvidenceType.BUILD, EvidenceStatus.PASSED, "github-actions")
                        .summary("Build OK")
                        .rawReference("build-log")
                        .build(),
                NormalizedEvidenceInput.builder(EvidenceType.TEST, EvidenceStatus.PASSED, "junit")
                        .summary("All tests passed")
                        .metricValue(new BigDecimal("10"))
                        .metricUnit("tests")
                        .build(),
                NormalizedEvidenceInput.builder(EvidenceType.CODE_COVERAGE, EvidenceStatus.PASSED, "jacoco")
                        .summary("Coverage 88%")
                        .metricValue(new BigDecimal("88.0"))
                        .metricUnit("percent")
                        .build(),
                NormalizedEvidenceInput.builder(EvidenceType.STATIC_ANALYSIS, EvidenceStatus.WARNING, "semgrep")
                        .summary("1 finding")
                        .findings(List.of(new NormalizedFindingInput(
                                FindingType.STATIC_ANALYSIS,
                                FindingSeverity.MEDIUM,
                                "rule-1",
                                "Hardcoded secret",
                                "Potential secret in source",
                                "src/App.java",
                                10,
                                null,
                                null,
                                null,
                                null
                        )))
                        .build(),
                NormalizedEvidenceInput.builder(EvidenceType.CONTAINER_SCAN, EvidenceStatus.FAILED, "trivy")
                        .summary("Critical CVE")
                        .findings(List.of(new NormalizedFindingInput(
                                FindingType.CONTAINER,
                                FindingSeverity.CRITICAL,
                                null,
                                "CVE-2024-0001",
                                "Critical container vuln",
                                null,
                                null,
                                "libfoo",
                                "1.0.0",
                                "1.0.1",
                                "CVE-2024-0001"
                        )))
                        .build(),
                NormalizedEvidenceInput.builder(EvidenceType.DEPENDENCY_SCAN, EvidenceStatus.PASSED, "trivy-fs")
                        .summary("Clean")
                        .build()
        );

        EvidenceIngestionResult result = evidenceIngestionService.ingest(
                new EvidenceIngestionRequest(deployment.getId(), items)
        );

        assertThat(result.deploymentId()).isEqualTo(deployment.getId());
        assertThat(result.evidenceCount()).isEqualTo(6);
        assertThat(result.findingCount()).isEqualTo(2);
        assertThat(result.evidenceIds()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(result.findingIds()).hasSize(2);

        List<DeploymentEvidence> stored = deploymentEvidenceRepository.findByDeploymentId(deployment.getId());
        assertThat(stored).hasSize(6);
        assertThat(stored).extracting(DeploymentEvidence::getEvidenceType)
                .contains(
                        EvidenceType.BUILD,
                        EvidenceType.TEST,
                        EvidenceType.CODE_COVERAGE,
                        EvidenceType.STATIC_ANALYSIS,
                        EvidenceType.CONTAINER_SCAN,
                        EvidenceType.DEPENDENCY_SCAN
                );

        List<Finding> findings = findingRepository.findByDeploymentId(deployment.getId());
        assertThat(findings).hasSize(2);
        assertThat(findings).anySatisfy(f -> {
            assertThat(f.getSeverity()).isEqualTo(FindingSeverity.CRITICAL);
            assertThat(f.getExternalId()).isEqualTo("CVE-2024-0001");
            assertThat(f.getEvidence()).isNotNull();
        });
    }

    @Test
    void ingestsSampleFixtureJson() throws Exception {
        ClassPathResource resource = new ClassPathResource("fixtures/evidence/sample-cicd-evidence.json");
        List<NormalizedEvidenceInput> items;
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            items = new ArrayList<>();
            for (JsonNode node : root.get("evidence")) {
                items.add(objectMapper.treeToValue(node, NormalizedEvidenceInput.class));
            }
        }

        EvidenceIngestionResult result = evidenceIngestionService.ingest(deployment.getId(), items);

        assertThat(result.evidenceCount()).isEqualTo(7);
        assertThat(result.findingCount()).isEqualTo(2);
        assertThat(deploymentEvidenceRepository.findByDeploymentIdAndEvidenceType(
                deployment.getId(), EvidenceType.WORKFLOW
        )).hasSize(1);
        assertThat(deploymentEvidenceRepository.findByDeploymentIdAndEvidenceType(
                deployment.getId(), EvidenceType.CODE_COVERAGE
        )).singleElement().satisfies(e -> {
            assertThat(e.getMetricValue()).isEqualByComparingTo("84.5");
            assertThat(e.getSource()).isEqualTo("jacoco");
        });
    }

    @Test
    void ingestsWorkflowRunAsNormalizedWorkflowEvidence() {
        GitHubWorkflowRunInfo run = new GitHubWorkflowRunInfo(
                99L,
                "CI",
                "CI — main",
                "completed",
                "success",
                "https://github.com/acme/payments/actions/runs/99",
                "main",
                "abc123",
                "push",
                7,
                Instant.parse("2026-03-01T11:00:00Z"),
                Instant.parse("2026-03-01T11:10:00Z"),
                Instant.parse("2026-03-01T11:00:05Z")
        );

        EvidenceIngestionResult result = evidenceIngestionService.ingestWorkflowRun(deployment.getId(), run);

        assertThat(result.evidenceCount()).isEqualTo(1);
        DeploymentEvidence evidence = deploymentEvidenceRepository
                .findByDeploymentIdAndEvidenceType(deployment.getId(), EvidenceType.WORKFLOW)
                .getFirst();
        assertThat(evidence.getStatus()).isEqualTo(EvidenceStatus.PASSED);
        assertThat(evidence.getSource()).isEqualTo("github-actions");
        assertThat(evidence.getRawReference()).contains("/actions/runs/99");

        Deployment updated = deploymentRepository.findById(deployment.getId()).orElseThrow();
        assertThat(updated.getWorkflowRunId()).isEqualTo(99L);
        assertThat(updated.getWorkflowName()).isEqualTo("CI");
        assertThat(updated.getWorkflowRunUrl()).isEqualTo("https://github.com/acme/payments/actions/runs/99");
    }

    @Test
    void buildEvidenceFromCiResultMapsConclusion() {
        NormalizedEvidenceInput failedBuild = evidenceIngestionService.buildEvidenceFromCiResult(
                EvidenceType.BUILD,
                "maven",
                "failure",
                null,
                "logs/build.txt"
        );
        assertThat(failedBuild.status()).isEqualTo(EvidenceStatus.FAILED);
        assertThat(failedBuild.summary()).contains("BUILD");

        EvidenceIngestionResult result = evidenceIngestionService.ingest(
                deployment.getId(),
                List.of(failedBuild)
        );
        assertThat(result.evidenceCount()).isEqualTo(1);
        assertThat(deploymentEvidenceRepository.findByDeploymentId(deployment.getId()))
                .singleElement()
                .extracting(DeploymentEvidence::getStatus)
                .isEqualTo(EvidenceStatus.FAILED);
    }

    @Test
    void rejectsUnknownDeploymentAndEmptyBatch() {
        assertThatThrownBy(() -> evidenceIngestionService.ingest(999_999L, List.of(
                NormalizedEvidenceInput.builder(EvidenceType.BUILD, EvidenceStatus.PASSED, "x").build()
        ))).isInstanceOf(DeploymentNotFoundException.class);

        assertThatThrownBy(() -> evidenceIngestionService.ingest(
                new EvidenceIngestionRequest(deployment.getId(), List.of())
        )).isInstanceOf(EvidenceIngestionException.class);

        assertThatThrownBy(() -> evidenceIngestionService.ingest(null))
                .isInstanceOf(EvidenceIngestionException.class);
    }
}
