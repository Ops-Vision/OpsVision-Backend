package com.opsvision.evidence.mapper;

import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import com.opsvision.evidence.dto.NormalizedFindingInput;
import com.opsvision.evidence.entity.DeploymentEvidence;
import com.opsvision.evidence.entity.Finding;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;
import org.springframework.stereotype.Component;

/**
 * Maps normalized ingestion DTOs to JPA entities (no GitHub/scanner coupling).
 */
@Component
public class EvidenceIngestionMapper {

    public DeploymentEvidence toEvidenceEntity(NormalizedEvidenceInput input) {
        if (input == null) {
            throw new IllegalArgumentException("evidence input must not be null");
        }
        if (input.evidenceType() == null) {
            throw new IllegalArgumentException("evidenceType is required");
        }
        if (input.status() == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (input.source() == null || input.source().isBlank()) {
            throw new IllegalArgumentException("source is required");
        }

        DeploymentEvidence entity = new DeploymentEvidence(
                input.evidenceType(),
                input.status(),
                input.source().trim(),
                truncate(input.summary(), 1024)
        );
        entity.setMetricValue(input.metricValue());
        entity.setMetricUnit(truncate(input.metricUnit(), 32));
        entity.setRawReference(truncate(input.rawReference(), 1024));
        if (input.collectedAt() != null) {
            entity.setCollectedAt(input.collectedAt());
        }
        return entity;
    }

    public Finding toFindingEntity(NormalizedFindingInput input) {
        if (input == null) {
            throw new IllegalArgumentException("finding input must not be null");
        }
        if (input.findingType() == null) {
            throw new IllegalArgumentException("findingType is required");
        }
        if (input.title() == null || input.title().isBlank()) {
            throw new IllegalArgumentException("finding title is required");
        }

        Finding finding = new Finding(
                input.findingType(),
                input.severity() != null ? input.severity() : FindingSeverity.UNKNOWN,
                truncate(input.title().trim(), 512),
                truncate(input.description(), 4000)
        );
        finding.setRuleId(truncate(input.ruleId(), 255));
        finding.setFilePath(truncate(input.filePath(), 1024));
        finding.setLineNumber(input.lineNumber());
        finding.setPackageName(truncate(input.packageName(), 512));
        finding.setInstalledVersion(truncate(input.installedVersion(), 128));
        finding.setFixedVersion(truncate(input.fixedVersion(), 128));
        finding.setExternalId(truncate(input.externalId(), 255));
        return finding;
    }

    /**
     * Maps common CI conclusion/status strings into {@link EvidenceStatus}.
     */
    public EvidenceStatus mapCiStatus(String statusOrConclusion) {
        if (statusOrConclusion == null || statusOrConclusion.isBlank()) {
            return EvidenceStatus.UNKNOWN;
        }
        return switch (statusOrConclusion.trim().toLowerCase()) {
            case "success", "passed", "pass", "completed_success", "ok" -> EvidenceStatus.PASSED;
            case "failure", "failed", "fail", "error", "timed_out", "startup_failure", "cancelled", "canceled" ->
                    EvidenceStatus.FAILED;
            case "neutral", "action_required", "stale", "warning", "unstable" -> EvidenceStatus.WARNING;
            case "skipped", "ignore", "ignored" -> EvidenceStatus.SKIPPED;
            case "in_progress", "queued", "requested", "waiting", "pending", "unknown" -> EvidenceStatus.UNKNOWN;
            default -> EvidenceStatus.UNKNOWN;
        };
    }

    /**
     * Infers a default finding type from evidence type when scanners have not classified yet.
     */
    public FindingType defaultFindingTypeFor(EvidenceType evidenceType) {
        if (evidenceType == null) {
            return FindingType.OTHER;
        }
        return switch (evidenceType) {
            case STATIC_ANALYSIS -> FindingType.STATIC_ANALYSIS;
            case DEPENDENCY_SCAN -> FindingType.DEPENDENCY;
            case CONTAINER_SCAN -> FindingType.CONTAINER;
            case CODE_COVERAGE -> FindingType.COVERAGE;
            case TEST -> FindingType.TEST_FAILURE;
            case BUILD, WORKFLOW, OTHER -> FindingType.OTHER;
        };
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
