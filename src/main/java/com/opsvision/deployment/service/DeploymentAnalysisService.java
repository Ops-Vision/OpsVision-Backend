package com.opsvision.deployment.service;

import com.opsvision.deployment.dto.AnalyzeDeploymentRequest;
import com.opsvision.deployment.dto.ConfidenceScoreResponse;
import com.opsvision.deployment.dto.DeploymentAnalysisResponse;
import com.opsvision.deployment.dto.DeploymentResponse;
import com.opsvision.deployment.dto.EvidenceResponse;
import com.opsvision.deployment.dto.FindingResponse;
import com.opsvision.deployment.dto.PolicyDecisionResponse;
import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.entity.ProjectRepository;
import com.opsvision.deployment.mapper.DeploymentAnalysisMapper;
import com.opsvision.deployment.model.DeploymentStatus;
import com.opsvision.deployment.repository.DeploymentRepository;
import com.opsvision.deployment.repository.ProjectRepositoryRepository;
import com.opsvision.evidence.dto.EvidenceIngestionRequest;
import com.opsvision.evidence.entity.DeploymentEvidence;
import com.opsvision.evidence.entity.Finding;
import com.opsvision.evidence.exception.DeploymentNotFoundException;
import com.opsvision.evidence.repository.DeploymentEvidenceRepository;
import com.opsvision.evidence.repository.FindingRepository;
import com.opsvision.evidence.service.EvidenceIngestionService;
import com.opsvision.policy.model.PolicyEvaluationResult;
import com.opsvision.policy.service.DeploymentPolicyEvaluator;
import com.opsvision.scoring.model.ConfidenceScoreResult;
import com.opsvision.scoring.model.ScoringEvidenceItem;
import com.opsvision.scoring.model.ScoringFindingItem;
import com.opsvision.scoring.service.DeploymentConfidenceScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates deployment registration, evidence loading, confidence scoring, and policy evaluation.
 */
@Service
public class DeploymentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentAnalysisService.class);

    private final ProjectRepositoryRepository projectRepositoryRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentEvidenceRepository deploymentEvidenceRepository;
    private final FindingRepository findingRepository;
    private final EvidenceIngestionService evidenceIngestionService;
    private final DeploymentConfidenceScorer confidenceScorer;
    private final DeploymentPolicyEvaluator policyEvaluator;
    private final DeploymentAnalysisMapper mapper;

    public DeploymentAnalysisService(
            ProjectRepositoryRepository projectRepositoryRepository,
            DeploymentRepository deploymentRepository,
            DeploymentEvidenceRepository deploymentEvidenceRepository,
            FindingRepository findingRepository,
            EvidenceIngestionService evidenceIngestionService,
            DeploymentConfidenceScorer confidenceScorer,
            DeploymentPolicyEvaluator policyEvaluator,
            DeploymentAnalysisMapper mapper
    ) {
        this.projectRepositoryRepository = projectRepositoryRepository;
        this.deploymentRepository = deploymentRepository;
        this.deploymentEvidenceRepository = deploymentEvidenceRepository;
        this.findingRepository = findingRepository;
        this.evidenceIngestionService = evidenceIngestionService;
        this.confidenceScorer = confidenceScorer;
        this.policyEvaluator = policyEvaluator;
        this.mapper = mapper;
    }

    /**
     * Create or reuse a deployment, optionally ingest evidence, then score and evaluate policy.
     */
    @Transactional
    public DeploymentAnalysisResponse analyze(AnalyzeDeploymentRequest request) {
        Objects.requireNonNull(request, "request");

        ProjectRepository repository = resolveRepository(request);
        Deployment deployment = resolveDeployment(repository, request);
        deployment.setStatus(DeploymentStatus.ANALYZING);
        deployment = deploymentRepository.save(deployment);

        if (request.evidence() != null && !request.evidence().isEmpty()) {
            evidenceIngestionService.ingest(new EvidenceIngestionRequest(deployment.getId(), request.evidence()));
        }

        markSucceeded(deployment.getId());
        return buildAnalysis(deployment.getId());
    }

    /**
     * Re-run score and policy for an existing deployment using stored evidence.
     */
    @Transactional
    public DeploymentAnalysisResponse reanalyze(Long deploymentId) {
        requireDeployment(deploymentId);
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException(deploymentId));
        deployment.setStatus(DeploymentStatus.ANALYZING);
        deploymentRepository.save(deployment);

        markSucceeded(deploymentId);
        return buildAnalysis(deploymentId);
    }

    @Transactional(readOnly = true)
    public DeploymentResponse getDeployment(Long deploymentId) {
        Deployment deployment = deploymentRepository.findByIdWithRepository(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException(deploymentId));
        return mapper.toDeploymentResponse(deployment);
    }

    @Transactional(readOnly = true)
    public Page<DeploymentResponse> listRecent(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return deploymentRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(safePage, safeSize))
                .map(d -> {
                    // Ensure repository is initialized within the transaction
                    d.getRepository().getFullName();
                    return mapper.toDeploymentResponse(d);
                });
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> listEvidence(Long deploymentId) {
        requireDeployment(deploymentId);
        return deploymentEvidenceRepository.findByDeploymentId(deploymentId).stream()
                .map(mapper::toEvidenceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FindingResponse> listFindings(Long deploymentId) {
        requireDeployment(deploymentId);
        return findingRepository.findByDeploymentId(deploymentId).stream()
                .map(mapper::toFindingResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConfidenceScoreResponse getScore(Long deploymentId) {
        requireDeployment(deploymentId);
        ConfidenceScoreResult score = computeScore(deploymentId);
        return mapper.toScoreResponse(deploymentId, score);
    }

    @Transactional(readOnly = true)
    public PolicyDecisionResponse getPolicy(Long deploymentId) {
        requireDeployment(deploymentId);
        List<DeploymentEvidence> evidence = deploymentEvidenceRepository.findByDeploymentId(deploymentId);
        List<Finding> findings = findingRepository.findByDeploymentId(deploymentId);
        List<ScoringEvidenceItem> scoringEvidence = mapper.toScoringEvidence(evidence, findings);
        List<ScoringFindingItem> unlinked = mapper.toUnlinkedScoringFindings(findings);
        ConfidenceScoreResult score = confidenceScorer.score(scoringEvidence, unlinked);
        PolicyEvaluationResult policy = policyEvaluator.evaluate(score, scoringEvidence, unlinked);
        return mapper.toPolicyResponse(deploymentId, policy);
    }

    @Transactional(readOnly = true)
    public DeploymentAnalysisResponse getAnalysis(Long deploymentId) {
        return buildAnalysis(deploymentId);
    }

    private DeploymentAnalysisResponse buildAnalysis(Long deploymentId) {
        Deployment deployment = deploymentRepository.findByIdWithRepository(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException(deploymentId));

        List<DeploymentEvidence> evidence = deploymentEvidenceRepository.findByDeploymentId(deploymentId);
        List<Finding> findings = findingRepository.findByDeploymentId(deploymentId);

        List<ScoringEvidenceItem> scoringEvidence = mapper.toScoringEvidence(evidence, findings);
        List<ScoringFindingItem> unlinked = mapper.toUnlinkedScoringFindings(findings);
        ConfidenceScoreResult scoreResult = confidenceScorer.score(scoringEvidence, unlinked);
        PolicyEvaluationResult policyResult = policyEvaluator.evaluate(scoreResult, scoringEvidence, unlinked);

        log.info(
                "Analyzed deployment {} score={} decision={}",
                deploymentId,
                scoreResult.score(),
                policyResult.decision()
        );

        return new DeploymentAnalysisResponse(
                mapper.toDeploymentResponse(deployment),
                evidence.stream().map(mapper::toEvidenceResponse).toList(),
                findings.stream().map(mapper::toFindingResponse).toList(),
                mapper.toScoreResponse(deploymentId, scoreResult),
                mapper.toPolicyResponse(deploymentId, policyResult)
        );
    }

    private ConfidenceScoreResult computeScore(Long deploymentId) {
        List<DeploymentEvidence> evidence = deploymentEvidenceRepository.findByDeploymentId(deploymentId);
        List<Finding> findings = findingRepository.findByDeploymentId(deploymentId);
        return confidenceScorer.score(
                mapper.toScoringEvidence(evidence, findings),
                mapper.toUnlinkedScoringFindings(findings)
        );
    }

    private ProjectRepository resolveRepository(AnalyzeDeploymentRequest request) {
        return projectRepositoryRepository
                .findByOwnerAndName(request.owner().trim(), request.repository().trim())
                .orElseGet(() -> {
                    String url = request.repositoryUrl();
                    if (url == null || url.isBlank()) {
                        url = "https://github.com/" + request.owner().trim() + "/" + request.repository().trim();
                    }
                    ProjectRepository created = new ProjectRepository(
                            request.owner().trim(),
                            request.repository().trim(),
                            request.branch(),
                            url
                    );
                    return projectRepositoryRepository.save(created);
                });
    }

    private Deployment resolveDeployment(ProjectRepository repository, AnalyzeDeploymentRequest request) {
        return deploymentRepository
                .findByRepositoryIdAndCommitShaAndEnvironment(
                        repository.getId(),
                        request.commitSha().trim(),
                        request.environment().trim()
                )
                .map(existing -> {
                    existing.setBranch(request.branch().trim());
                    if (request.workflowName() != null && !request.workflowName().isBlank()) {
                        existing.setWorkflowName(request.workflowName().trim());
                    }
                    if (request.workflowRunId() != null) {
                        existing.setWorkflowRunId(request.workflowRunId());
                    }
                    if (request.workflowRunUrl() != null && !request.workflowRunUrl().isBlank()) {
                        existing.setWorkflowRunUrl(request.workflowRunUrl().trim());
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    Deployment created = new Deployment(
                            repository,
                            request.commitSha().trim(),
                            request.branch().trim(),
                            request.environment().trim(),
                            DeploymentStatus.PENDING
                    );
                    created.setWorkflowName(request.workflowName());
                    created.setWorkflowRunId(request.workflowRunId());
                    created.setWorkflowRunUrl(request.workflowRunUrl());
                    return created;
                });
    }

    private void markSucceeded(Long deploymentId) {
        deploymentRepository.findById(deploymentId).ifPresent(d -> {
            d.setStatus(DeploymentStatus.SUCCEEDED);
            deploymentRepository.save(d);
        });
    }

    private void requireDeployment(Long deploymentId) {
        if (deploymentId == null || !deploymentRepository.existsById(deploymentId)) {
            throw new DeploymentNotFoundException(deploymentId);
        }
    }
}
