package com.opsvision.scoring.model;

import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Evidence snapshot used by the confidence engine (decoupled from JPA entities).
 */
public record ScoringEvidenceItem(
        EvidenceType type,
        EvidenceStatus status,
        String source,
        String summary,
        BigDecimal metricValue,
        String metricUnit,
        List<ScoringFindingItem> findings
) {
    public ScoringEvidenceItem {
        if (type == null) {
            throw new IllegalArgumentException("evidence type is required");
        }
        if (status == null) {
            status = EvidenceStatus.UNKNOWN;
        }
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public static ScoringEvidenceItem of(EvidenceType type, EvidenceStatus status) {
        return new ScoringEvidenceItem(type, status, null, null, null, null, List.of());
    }

    public static ScoringEvidenceItem of(
            EvidenceType type,
            EvidenceStatus status,
            BigDecimal metricValue,
            List<ScoringFindingItem> findings
    ) {
        return new ScoringEvidenceItem(type, status, null, null, metricValue, null, findings);
    }
}
