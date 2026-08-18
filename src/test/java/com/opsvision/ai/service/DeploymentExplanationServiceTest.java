package com.opsvision.ai.service;

import com.opsvision.ai.exception.AiProviderException;
import com.opsvision.ai.model.DeploymentExplanation;
import com.opsvision.ai.model.DeploymentExplanationRequest;
import com.opsvision.ai.provider.AiProvider;
import com.opsvision.deployment.dto.DeploymentExplanationResponse;
import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.entity.ProjectRepository;
import com.opsvision.deployment.mapper.DeploymentAnalysisMapper;
import com.opsvision.deployment.model.DeploymentStatus;
import com.opsvision.deployment.repository.DeploymentRepository;
import com.opsvision.evidence.entity.DeploymentEvidence;
import com.opsvision.evidence.entity.Finding;
import com.opsvision.evidence.exception.DeploymentNotFoundException;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;
import com.opsvision.evidence.repository.DeploymentEvidenceRepository;
import com.opsvision.evidence.repository.FindingRepository;
import com.opsvision.policy.model.PolicyDecision;
import com.opsvision.policy.model.PolicyEvaluationResult;
import com.opsvision.policy.service.DeploymentPolicyEvaluator;
import com.opsvision.scoring.model.ConfidenceScoreResult;
import com.opsvision.scoring.model.ScoreFactor;
import com.opsvision.scoring.service.DeploymentConfidenceScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentExplanationServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private DeploymentEvidenceRepository deploymentEvidenceRepository;
    @Mock
    private FindingRepository findingRepository;
    @Mock
    private DeploymentConfidenceScorer confidenceScorer;
    @Mock
    private DeploymentPolicyEvaluator policyEvaluator;
    @Mock
    private AiProvider aiProvider;

    private DeploymentAnalysisMapper mapper;
    private DeploymentExplanationService service;

    @BeforeEach
    void setUp() {
        mapper = new DeploymentAnalysisMapper();
        service = new DeploymentExplanationService(
                deploymentRepository,
                deploymentEvidenceRepository,
                findingRepository,
                confidenceScorer,
                policyEvaluator,
                mapper,
                aiProvider
        );
    }

    @Test
    void explainBuildsContextAndMapsProviderResult() {
        Long id = 7L;
        Deployment deployment = sampleDeployment(id);
        when(deploymentRepository.findByIdWithRepository(id)).thenReturn(Optional.of(deployment));

        DeploymentEvidence build = new DeploymentEvidence(
                EvidenceType.BUILD, EvidenceStatus.PASSED, "ci", "Build OK"
        );
        build.setDeployment(deployment);
        DeploymentEvidence coverage = new DeploymentEvidence(
                EvidenceType.CODE_COVERAGE, EvidenceStatus.PASSED, "jacoco", "Coverage"
        );
        coverage.setDeployment(deployment);
        coverage.setMetricValue(new BigDecimal("72.5"));

        Finding finding = new Finding(
                FindingType.STATIC_ANALYSIS,
                FindingSeverity.HIGH,
                "Possible injection",
                "desc"
        );
        finding.setDeployment(deployment);
        finding.setRuleId("java.lang.security.audit");
        finding.setFilePath("src/A.java");

        when(deploymentEvidenceRepository.findByDeploymentId(id)).thenReturn(List.of(build, coverage));
        when(findingRepository.findByDeploymentId(id)).thenReturn(List.of(finding));

        ConfidenceScoreResult score = new ConfidenceScoreResult(
                70,
                List.of(new ScoreFactor("build", 20, 20, "Build passed"))
        );
        when(confidenceScorer.score(anyList(), anyList())).thenReturn(score);
        when(policyEvaluator.evaluate(any(), anyList(), anyList())).thenReturn(
                PolicyEvaluationResult.of(PolicyDecision.REVIEW, List.of("Score requires review"), 70)
        );

        when(aiProvider.name()).thenReturn("mock");
        when(aiProvider.generateDeploymentExplanation(any())).thenReturn(
                new DeploymentExplanation(
                        "Moderate risk due to high finding",
                        List.of("HIGH SAST finding"),
                        List.of("Fix injection before prod"),
                        "mock",
                        "mock-model",
                        true
                )
        );

        DeploymentExplanationResponse response = service.explain(id);

        assertThat(response.deploymentId()).isEqualTo(id);
        assertThat(response.available()).isTrue();
        assertThat(response.summary()).contains("Moderate risk");
        assertThat(response.concerns()).containsExactly("HIGH SAST finding");
        assertThat(response.remediations()).containsExactly("Fix injection before prod");
        assertThat(response.provider()).isEqualTo("mock");
        assertThat(response.model()).isEqualTo("mock-model");

        ArgumentCaptor<DeploymentExplanationRequest> captor =
                ArgumentCaptor.forClass(DeploymentExplanationRequest.class);
        verify(aiProvider).generateDeploymentExplanation(captor.capture());
        DeploymentExplanationRequest ctx = captor.getValue();
        assertThat(ctx.deploymentId()).isEqualTo(id);
        assertThat(ctx.owner()).isEqualTo("acme");
        assertThat(ctx.repository()).isEqualTo("payments");
        assertThat(ctx.commitSha()).isEqualTo("abc123");
        assertThat(ctx.confidenceScore()).isEqualTo(70);
        assertThat(ctx.policyDecision()).isEqualTo("REVIEW");
        assertThat(ctx.policyReasons()).contains("Score requires review");
        assertThat(ctx.evidence()).hasSize(2);
        assertThat(ctx.findings()).hasSize(1);
        assertThat(ctx.findings().getFirst().severity()).isEqualTo("HIGH");
        assertThat(ctx.factors()).extracting(DeploymentExplanationRequest.FactorContext::name)
                .contains("build");
    }

    @Test
    void explainMissingDeploymentThrows() {
        when(deploymentRepository.findByIdWithRepository(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.explain(99L))
                .isInstanceOf(DeploymentNotFoundException.class);
    }

    @Test
    void providerFailurePropagates() {
        Long id = 3L;
        when(deploymentRepository.findByIdWithRepository(id)).thenReturn(Optional.of(sampleDeployment(id)));
        when(deploymentEvidenceRepository.findByDeploymentId(id)).thenReturn(List.of());
        when(findingRepository.findByDeploymentId(id)).thenReturn(List.of());
        when(confidenceScorer.score(anyList(), anyList())).thenReturn(
                new ConfidenceScoreResult(50, List.of())
        );
        when(policyEvaluator.evaluate(any(), anyList(), anyList())).thenReturn(
                PolicyEvaluationResult.of(PolicyDecision.BLOCK, List.of("low score"), 50)
        );
        when(aiProvider.name()).thenReturn("openai-compatible");
        when(aiProvider.generateDeploymentExplanation(any()))
                .thenThrow(new AiProviderException("upstream timeout"));

        assertThatThrownBy(() -> service.explain(id))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void disabledProviderResultIsMapped() {
        Long id = 4L;
        when(deploymentRepository.findByIdWithRepository(id)).thenReturn(Optional.of(sampleDeployment(id)));
        when(deploymentEvidenceRepository.findByDeploymentId(id)).thenReturn(List.of());
        when(findingRepository.findByDeploymentId(id)).thenReturn(List.of());
        when(confidenceScorer.score(anyList(), anyList())).thenReturn(
                new ConfidenceScoreResult(90, List.of())
        );
        when(policyEvaluator.evaluate(any(), anyList(), anyList())).thenReturn(
                PolicyEvaluationResult.of(PolicyDecision.DEPLOY, List.of("ok"), 90)
        );
        when(aiProvider.name()).thenReturn("none");
        when(aiProvider.generateDeploymentExplanation(any())).thenReturn(
                DeploymentExplanation.unavailable("none", "AI explanations are disabled")
        );

        DeploymentExplanationResponse response = service.explain(id);

        assertThat(response.available()).isFalse();
        assertThat(response.summary()).containsIgnoringCase("disabled");
        assertThat(response.provider()).isEqualTo("none");
    }

    private static Deployment sampleDeployment(Long id) {
        ProjectRepository repo = new ProjectRepository(
                "acme", "payments", "main", "https://github.com/acme/payments"
        );
        try {
            var idField = ProjectRepository.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(repo, 10L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        Deployment deployment = new Deployment(repo, "abc123", "main", "staging", DeploymentStatus.SUCCEEDED);
        deployment.setWorkflowName("CI");
        try {
            var idField = Deployment.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(deployment, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return deployment;
    }
}
