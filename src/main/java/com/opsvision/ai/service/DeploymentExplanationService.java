package com.opsvision.ai.service;

import com.opsvision.ai.model.DeploymentExplanation;
import com.opsvision.ai.model.DeploymentExplanationRequest;
import com.opsvision.ai.provider.AiProvider;
import com.opsvision.deployment.dto.DeploymentExplanationResponse;
import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.entity.ProjectRepository;
import com.opsvision.deployment.mapper.DeploymentAnalysisMapper;
import com.opsvision.deployment.repository.DeploymentRepository;
import com.opsvision.evidence.entity.DeploymentEvidence;
import com.opsvision.evidence.entity.Finding;
import com.opsvision.evidence.exception.DeploymentNotFoundException;
import com.opsvision.evidence.repository.DeploymentEvidenceRepository;
import com.opsvision.evidence.repository.FindingRepository;
import com.opsvision.policy.model.PolicyEvaluationResult;
import com.opsvision.policy.service.DeploymentPolicyEvaluator;
import com.opsvision.scoring.model.ConfidenceScoreResult;
import com.opsvision.scoring.model.ScoreFactor;
import com.opsvision.scoring.model.ScoringEvidenceItem;
import com.opsvision.scoring.model.ScoringFindingItem;
import com.opsvision.scoring.service.DeploymentConfidenceScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Builds structured explanation context from deterministic score/policy/evidence and calls {@link AiProvider}.
 * Does not write scores or policy decisions.
 */
@Service
public class DeploymentExplanationService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentExplanationService.class);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEvidenceRepository deploymentEvidenceRepository;
    private final FindingRepository findingRepository;
    private final DeploymentConfidenceScorer confidenceScorer;
    private final DeploymentPolicyEvaluator policyEvaluator;
    private final DeploymentAnalysisMapper mapper;
    private final AiProvider aiProvider;

    public DeploymentExplanationService(
            DeploymentRepository deploymentRepository,
            DeploymentEvidenceRepository deploymentEvidenceRepository,
            FindingRepository findingRepository,
            DeploymentConfidenceScorer confidenceScorer,
            DeploymentPolicyEvaluator policyEvaluator,
            DeploymentAnalysisMapper mapper,
            AiProvider aiProvider
    ) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentEvidenceRepository = deploymentEvidenceRepository;
        this.findingRepository = findingRepository;
        this.confidenceScorer = confidenceScorer;
        this.policyEvaluator = policyEvaluator;
        this.mapper = mapper;
        this.aiProvider = aiProvider;
    }

    @Transactional(readOnly = true)
    public DeploymentExplanationResponse explain(Long deploymentId) {
        Deployment deployment = deploymentRepository.findByIdWithRepository(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException(deploymentId));

        List<DeploymentEvidence> evidence = deploymentEvidenceRepository.findByDeploymentId(deploymentId);
        List<Finding> findings = findingRepository.findByDeploymentId(deploymentId);

        List<ScoringEvidenceItem> scoringEvidence = mapper.toScoringEvidence(evidence, findings);
        List<ScoringFindingItem> unlinked = mapper.toUnlinkedScoringFindings(findings);
        ConfidenceScoreResult scoreResult = confidenceScorer.score(scoringEvidence, unlinked);
        PolicyEvaluationResult policyResult = policyEvaluator.evaluate(scoreResult, scoringEvidence, unlinked);

        DeploymentExplanationRequest request = toRequest(deployment, scoreResult, policyResult, evidence, findings);

        log.debug("Requesting AI explanation for deployment {} via provider={}", deploymentId, aiProvider.name());
        DeploymentExplanation explanation = aiProvider.generateDeploymentExplanation(request);

        return new DeploymentExplanationResponse(
                deploymentId,
                explanation.summary(),
                explanation.concerns(),
                explanation.remediations(),
                explanation.provider(),
                explanation.model(),
                explanation.available()
        );
    }

    private static DeploymentExplanationRequest toRequest(
            Deployment deployment,
            ConfidenceScoreResult scoreResult,
            PolicyEvaluationResult policyResult,
            List<DeploymentEvidence> evidence,
            List<Finding> findings
    ) {
        ProjectRepository repo = deployment.getRepository();
        String owner = repo != null ? repo.getOwner() : null;
        String name = repo != null ? repo.getName() : null;

        List<DeploymentExplanationRequest.FactorContext> factors = scoreResult.factors().stream()
                .map(DeploymentExplanationService::toFactor)
                .toList();

        List<DeploymentExplanationRequest.EvidenceContext> evidenceCtx = evidence.stream()
                .map(e -> new DeploymentExplanationRequest.EvidenceContext(
                        e.getEvidenceType() != null ? e.getEvidenceType().name() : null,
                        e.getStatus() != null ? e.getStatus().name() : null,
                        e.getSource(),
                        e.getSummary(),
                        e.getMetricValue() != null ? e.getMetricValue().toPlainString() : null
                ))
                .toList();

        List<DeploymentExplanationRequest.FindingContext> findingCtx = findings.stream()
                .map(f -> new DeploymentExplanationRequest.FindingContext(
                        f.getFindingType() != null ? f.getFindingType().name() : null,
                        f.getSeverity() != null ? f.getSeverity().name() : null,
                        f.getTitle(),
                        f.getRuleId(),
                        f.getFilePath()
                ))
                .toList();

        return new DeploymentExplanationRequest(
                deployment.getId(),
                owner,
                name,
                deployment.getCommitSha(),
                deployment.getBranch(),
                deployment.getEnvironment(),
                deployment.getWorkflowName(),
                scoreResult.score(),
                factors,
                policyResult.decision() != null ? policyResult.decision().name() : null,
                policyResult.reasons(),
                evidenceCtx,
                findingCtx
        );
    }

    private static DeploymentExplanationRequest.FactorContext toFactor(ScoreFactor factor) {
        return new DeploymentExplanationRequest.FactorContext(
                factor.name(),
                factor.score(),
                factor.maxScore(),
                factor.reason()
        );
    }
}
