package com.opsvision.policy.model;

import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.scoring.model.ConfidenceScoreResult;
import com.opsvision.scoring.model.ScoringEvidenceItem;
import com.opsvision.scoring.model.ScoringFindingItem;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Snapshot consumed by the policy engine (decoupled from JPA).
 */
public record PolicyEvaluationInput(
        int confidenceScore,
        Map<EvidenceType, EvidenceStatus> evidenceStatuses,
        List<FindingSeverity> findingSeverities
) {
    public PolicyEvaluationInput {
        if (confidenceScore < 0) {
            confidenceScore = 0;
        }
        if (confidenceScore > 100) {
            confidenceScore = 100;
        }
        evidenceStatuses = evidenceStatuses == null
                ? Map.of()
                : Map.copyOf(evidenceStatuses);
        findingSeverities = findingSeverities == null
                ? List.of()
                : List.copyOf(findingSeverities);
    }

    public static PolicyEvaluationInput fromScore(ConfidenceScoreResult scoreResult) {
        Objects.requireNonNull(scoreResult, "scoreResult");
        return new PolicyEvaluationInput(scoreResult.score(), Map.of(), List.of());
    }

    public static PolicyEvaluationInput of(
            ConfidenceScoreResult scoreResult,
            Collection<ScoringEvidenceItem> evidence,
            Collection<ScoringFindingItem> additionalFindings
    ) {
        Objects.requireNonNull(scoreResult, "scoreResult");
        Map<EvidenceType, EvidenceStatus> statuses = new EnumMap<>(EvidenceType.class);
        EnumSet<FindingSeverity> severities = EnumSet.noneOf(FindingSeverity.class);

        if (evidence != null) {
            for (ScoringEvidenceItem item : evidence) {
                if (item == null) {
                    continue;
                }
                EvidenceStatus existing = statuses.get(item.type());
                statuses.put(item.type(), worse(existing, item.status()));
                if (item.findings() != null) {
                    for (ScoringFindingItem f : item.findings()) {
                        if (f != null && f.severity() != null) {
                            severities.add(f.severity());
                        }
                    }
                }
            }
        }
        if (additionalFindings != null) {
            for (ScoringFindingItem f : additionalFindings) {
                if (f != null && f.severity() != null) {
                    severities.add(f.severity());
                }
            }
        }

        return new PolicyEvaluationInput(
                scoreResult.score(),
                statuses,
                List.copyOf(severities)
        );
    }

    public boolean hasFailedBuild() {
        return isFailed(EvidenceType.BUILD);
    }

    public boolean hasFailedTests() {
        return isFailed(EvidenceType.TEST);
    }

    public boolean hasFailedWorkflow() {
        return isFailed(EvidenceType.WORKFLOW);
    }

    public boolean hasCriticalFinding() {
        return findingSeverities.contains(FindingSeverity.CRITICAL);
    }

    public boolean hasHighFinding() {
        return findingSeverities.contains(FindingSeverity.HIGH);
    }

    public Set<FindingSeverity> severitySet() {
        return findingSeverities.isEmpty()
                ? Set.of()
                : EnumSet.copyOf(findingSeverities);
    }

    private boolean isFailed(EvidenceType type) {
        EvidenceStatus status = evidenceStatuses.get(type);
        return status == EvidenceStatus.FAILED;
    }

    private static EvidenceStatus worse(EvidenceStatus a, EvidenceStatus b) {
        if (a == null) {
            return b == null ? EvidenceStatus.UNKNOWN : b;
        }
        if (b == null) {
            return a;
        }
        return rank(b) > rank(a) ? b : a;
    }

    private static int rank(EvidenceStatus status) {
        return switch (status) {
            case FAILED -> 4;
            case WARNING -> 3;
            case UNKNOWN -> 2;
            case SKIPPED -> 1;
            case PASSED -> 0;
        };
    }
}
