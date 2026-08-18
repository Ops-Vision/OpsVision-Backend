package com.opsvision.deployment.mapper;

import com.opsvision.deployment.dto.ConfidenceScoreResponse;
import com.opsvision.deployment.dto.DeploymentResponse;
import com.opsvision.deployment.dto.EvidenceResponse;
import com.opsvision.deployment.dto.FindingResponse;
import com.opsvision.deployment.dto.PolicyDecisionResponse;
import com.opsvision.deployment.dto.RepositorySummaryDto;
import com.opsvision.deployment.dto.ScoreFactorResponse;
import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.entity.ProjectRepository;
import com.opsvision.evidence.entity.DeploymentEvidence;
import com.opsvision.evidence.entity.Finding;
import com.opsvision.policy.model.PolicyEvaluationResult;
import com.opsvision.scoring.model.ConfidenceScoreResult;
import com.opsvision.scoring.model.ScoreFactor;
import com.opsvision.scoring.model.ScoringEvidenceItem;
import com.opsvision.scoring.model.ScoringFindingItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps deployment domain entities and analysis results to API DTOs (never exposes JPA entities).
 */
@Component
public class DeploymentAnalysisMapper {

    public DeploymentResponse toDeploymentResponse(Deployment deployment) {
        Objects.requireNonNull(deployment, "deployment");
        return new DeploymentResponse(
                deployment.getId(),
                toRepositorySummary(deployment.getRepository()),
                deployment.getCommitSha(),
                deployment.getBranch(),
                deployment.getEnvironment(),
                deployment.getStatus(),
                deployment.getWorkflowName(),
                deployment.getWorkflowRunId(),
                deployment.getWorkflowRunUrl(),
                deployment.getDeployedAt(),
                deployment.getCreatedAt(),
                deployment.getUpdatedAt()
        );
    }

    public RepositorySummaryDto toRepositorySummary(ProjectRepository repository) {
        if (repository == null) {
            return null;
        }
        return new RepositorySummaryDto(
                repository.getId(),
                repository.getOwner(),
                repository.getName(),
                repository.getFullName(),
                repository.getUrl()
        );
    }

    public EvidenceResponse toEvidenceResponse(DeploymentEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        Long deploymentId = evidence.getDeployment() != null ? evidence.getDeployment().getId() : null;
        return new EvidenceResponse(
                evidence.getId(),
                deploymentId,
                evidence.getEvidenceType(),
                evidence.getStatus(),
                evidence.getSource(),
                evidence.getSummary(),
                evidence.getMetricValue(),
                evidence.getMetricUnit(),
                evidence.getRawReference(),
                evidence.getCollectedAt(),
                evidence.getCreatedAt()
        );
    }

    public FindingResponse toFindingResponse(Finding finding) {
        Objects.requireNonNull(finding, "finding");
        Long deploymentId = finding.getDeployment() != null ? finding.getDeployment().getId() : null;
        Long evidenceId = finding.getEvidence() != null ? finding.getEvidence().getId() : null;
        return new FindingResponse(
                finding.getId(),
                deploymentId,
                evidenceId,
                finding.getFindingType(),
                finding.getSeverity(),
                finding.getRuleId(),
                finding.getTitle(),
                finding.getDescription(),
                finding.getFilePath(),
                finding.getLineNumber(),
                finding.getPackageName(),
                finding.getInstalledVersion(),
                finding.getFixedVersion(),
                finding.getExternalId(),
                finding.getCreatedAt()
        );
    }

    public ConfidenceScoreResponse toScoreResponse(Long deploymentId, ConfidenceScoreResult result) {
        Objects.requireNonNull(result, "result");
        List<ScoreFactorResponse> factors = result.factors().stream()
                .map(this::toFactorResponse)
                .toList();
        return new ConfidenceScoreResponse(deploymentId, result.score(), factors);
    }

    public ScoreFactorResponse toFactorResponse(ScoreFactor factor) {
        return new ScoreFactorResponse(factor.name(), factor.score(), factor.maxScore(), factor.reason());
    }

    public PolicyDecisionResponse toPolicyResponse(Long deploymentId, PolicyEvaluationResult result) {
        Objects.requireNonNull(result, "result");
        return new PolicyDecisionResponse(
                deploymentId,
                result.decision(),
                result.reasons(),
                result.score()
        );
    }

    /**
     * Build scoring snapshots: findings nested under their evidence when linked.
     */
    public List<ScoringEvidenceItem> toScoringEvidence(
            List<DeploymentEvidence> evidenceItems,
            List<Finding> findings
    ) {
        List<DeploymentEvidence> evidence = evidenceItems == null ? List.of() : evidenceItems;
        List<Finding> allFindings = findings == null ? List.of() : findings;

        Map<Long, List<ScoringFindingItem>> byEvidenceId = new HashMap<>();
        List<ScoringFindingItem> unlinked = new ArrayList<>();

        for (Finding finding : allFindings) {
            if (finding == null) {
                continue;
            }
            ScoringFindingItem item = new ScoringFindingItem(
                    finding.getFindingType(),
                    finding.getSeverity(),
                    finding.getTitle()
            );
            if (finding.getEvidence() != null && finding.getEvidence().getId() != null) {
                byEvidenceId
                        .computeIfAbsent(finding.getEvidence().getId(), id -> new ArrayList<>())
                        .add(item);
            } else {
                unlinked.add(item);
            }
        }

        List<ScoringEvidenceItem> result = new ArrayList<>();
        for (DeploymentEvidence e : evidence) {
            if (e == null) {
                continue;
            }
            List<ScoringFindingItem> nested = byEvidenceId.getOrDefault(e.getId(), List.of());
            result.add(new ScoringEvidenceItem(
                    e.getEvidenceType(),
                    e.getStatus(),
                    e.getSource(),
                    e.getSummary(),
                    e.getMetricValue(),
                    e.getMetricUnit(),
                    nested
            ));
        }

        // Represent unlinked findings as a synthetic bucket only if needed by caller via extra list.
        // Callers should pass unlinked separately; we keep them accessible via extractUnlinkedFindings.
        return List.copyOf(result);
    }

    public List<ScoringFindingItem> toUnlinkedScoringFindings(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }
        return findings.stream()
                .filter(Objects::nonNull)
                .filter(f -> f.getEvidence() == null || f.getEvidence().getId() == null)
                .map(f -> new ScoringFindingItem(f.getFindingType(), f.getSeverity(), f.getTitle()))
                .toList();
    }
}
