package com.opsvision.evidence.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.repository.DeploymentRepository;
import com.opsvision.evidence.dto.EvidenceIngestionRequest;
import com.opsvision.evidence.dto.EvidenceIngestionResult;
import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import com.opsvision.evidence.dto.NormalizedFindingInput;
import com.opsvision.evidence.entity.DeploymentEvidence;
import com.opsvision.evidence.entity.Finding;
import com.opsvision.evidence.exception.DeploymentNotFoundException;
import com.opsvision.evidence.exception.EvidenceIngestionException;
import com.opsvision.evidence.mapper.EvidenceIngestionMapper;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.repository.DeploymentEvidenceRepository;
import com.opsvision.evidence.repository.FindingRepository;
import com.opsvision.github.model.GitHubWorkflowRunInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts normalized CI/CD evidence (and optional nested findings) into persisted domain entities.
 * Does not compute deployment confidence scores or policy decisions.
 */
@Service
public class EvidenceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceIngestionService.class);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEvidenceRepository deploymentEvidenceRepository;
    private final FindingRepository findingRepository;
    private final EvidenceIngestionMapper mapper;

    public EvidenceIngestionService(
            DeploymentRepository deploymentRepository,
            DeploymentEvidenceRepository deploymentEvidenceRepository,
            FindingRepository findingRepository,
            EvidenceIngestionMapper mapper
    ) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentEvidenceRepository = deploymentEvidenceRepository;
        this.findingRepository = findingRepository;
        this.mapper = mapper;
    }

    /**
     * Persist a batch of normalized evidence items (and nested findings) for a deployment.
     */
    @Transactional
    public EvidenceIngestionResult ingest(EvidenceIngestionRequest request) {
        if (request == null) {
            throw new EvidenceIngestionException("ingestion request must not be null");
        }
        if (request.deploymentId() == null) {
            throw new EvidenceIngestionException("deploymentId is required");
        }
        if (request.evidence() == null || request.evidence().isEmpty()) {
            throw new EvidenceIngestionException("at least one evidence item is required");
        }

        Deployment deployment = deploymentRepository.findById(request.deploymentId())
                .orElseThrow(() -> new DeploymentNotFoundException(request.deploymentId()));

        return persistEvidence(deployment, request.evidence());
    }

    /**
     * Convenience overload when the caller already holds a managed or detached deployment id + inputs.
     */
    @Transactional
    public EvidenceIngestionResult ingest(Long deploymentId, List<NormalizedEvidenceInput> evidenceItems) {
        return ingest(new EvidenceIngestionRequest(deploymentId, evidenceItems));
    }

    /**
     * Maps a GitHub Actions workflow run into normalized WORKFLOW evidence and persists it.
     * Keeps GitHub response objects out of the scoring path by converting first.
     */
    @Transactional
    public EvidenceIngestionResult ingestWorkflowRun(Long deploymentId, GitHubWorkflowRunInfo run) {
        if (run == null) {
            throw new EvidenceIngestionException("workflow run must not be null");
        }

        EvidenceStatus status = resolveWorkflowStatus(run.status(), run.conclusion());
        String summary = buildWorkflowSummary(run);
        String rawRef = run.htmlUrl() != null
                ? run.htmlUrl()
                : (run.id() > 0 ? "github-workflow-run:" + run.id() : null);

        NormalizedEvidenceInput input = NormalizedEvidenceInput.builder(
                        EvidenceType.WORKFLOW,
                        status,
                        "github-actions"
                )
                .summary(summary)
                .rawReference(rawRef)
                .collectedAt(run.updatedAt() != null ? run.updatedAt() : Instant.now())
                .build();

        EvidenceIngestionResult result = ingest(deploymentId, List.of(input));

        deploymentRepository.findById(deploymentId).ifPresent(deployment -> {
            boolean dirty = false;
            if (run.name() != null && !run.name().isBlank()
                    && (deployment.getWorkflowName() == null || deployment.getWorkflowName().isBlank())) {
                deployment.setWorkflowName(run.name());
                dirty = true;
            }
            if (run.id() > 0 && deployment.getWorkflowRunId() == null) {
                deployment.setWorkflowRunId(run.id());
                dirty = true;
            }
            if (run.htmlUrl() != null && !run.htmlUrl().isBlank()
                    && (deployment.getWorkflowRunUrl() == null || deployment.getWorkflowRunUrl().isBlank())) {
                deployment.setWorkflowRunUrl(run.htmlUrl());
                dirty = true;
            }
            if (dirty) {
                deploymentRepository.save(deployment);
            }
        });

        return result;
    }

    /**
     * Builds a single BUILD evidence item from a CI build conclusion string (fixture-friendly).
     */
    public NormalizedEvidenceInput buildEvidenceFromCiResult(
            EvidenceType type,
            String source,
            String conclusion,
            String summary,
            String rawReference
    ) {
        Objects.requireNonNull(type, "type");
        if (source == null || source.isBlank()) {
            throw new EvidenceIngestionException("source is required");
        }
        EvidenceStatus status = mapper.mapCiStatus(conclusion);
        return NormalizedEvidenceInput.builder(type, status, source)
                .summary(summary != null ? summary : defaultSummary(type, status))
                .rawReference(rawReference)
                .collectedAt(Instant.now())
                .build();
    }

    private EvidenceIngestionResult persistEvidence(Deployment deployment, List<NormalizedEvidenceInput> items) {
        List<DeploymentEvidence> savedEvidenceItems = new ArrayList<>();
        List<Finding> savedFindings = new ArrayList<>();

        for (NormalizedEvidenceInput item : items) {
            if (item == null) {
                throw new EvidenceIngestionException("evidence item must not be null");
            }
            DeploymentEvidence evidenceEntity = mapper.toEvidenceEntity(item);
            evidenceEntity.setDeployment(deployment);
            DeploymentEvidence savedEvidence = deploymentEvidenceRepository.save(evidenceEntity);
            savedEvidenceItems.add(savedEvidence);

            List<NormalizedFindingInput> findings = item.findings();
            if (findings != null) {
                for (NormalizedFindingInput findingInput : findings) {
                    Finding findingEntity = mapper.toFindingEntity(findingInput);
                    findingEntity.setDeployment(deployment);
                    findingEntity.setEvidence(savedEvidence);
                    savedFindings.add(findingRepository.save(findingEntity));
                }
            }
        }

        deploymentEvidenceRepository.flush();
        findingRepository.flush();

        List<Long> evidenceIds = savedEvidenceItems.stream()
                .map(DeploymentEvidence::getId)
                .filter(Objects::nonNull)
                .toList();
        List<Long> findingIds = savedFindings.stream()
                .map(Finding::getId)
                .filter(Objects::nonNull)
                .toList();

        log.info(
                "Ingested {} evidence item(s) and {} finding(s) for deployment {}",
                evidenceIds.size(),
                findingIds.size(),
                deployment.getId()
        );

        return new EvidenceIngestionResult(
                deployment.getId(),
                evidenceIds.size(),
                findingIds.size(),
                evidenceIds,
                findingIds
        );
    }

    private EvidenceStatus resolveWorkflowStatus(String status, String conclusion) {
        if (conclusion != null && !conclusion.isBlank()) {
            return mapper.mapCiStatus(conclusion);
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toLowerCase();
            if ("completed".equals(normalized)) {
                return EvidenceStatus.UNKNOWN;
            }
            return mapper.mapCiStatus(status);
        }
        return EvidenceStatus.UNKNOWN;
    }

    private static String buildWorkflowSummary(GitHubWorkflowRunInfo run) {
        String name = run.displayTitle() != null && !run.displayTitle().isBlank()
                ? run.displayTitle()
                : (run.name() != null ? run.name() : "workflow");
        String conclusion = run.conclusion() != null ? run.conclusion() : "n/a";
        String status = run.status() != null ? run.status() : "n/a";
        return "Workflow '" + name + "' status=" + status + " conclusion=" + conclusion;
    }

    private static String defaultSummary(EvidenceType type, EvidenceStatus status) {
        return type.name() + " evidence " + status.name().toLowerCase();
    }
}
