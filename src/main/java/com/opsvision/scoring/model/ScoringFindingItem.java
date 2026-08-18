package com.opsvision.scoring.model;

import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;

/**
 * Finding snapshot used by the confidence engine (decoupled from JPA entities).
 */
public record ScoringFindingItem(
        FindingType type,
        FindingSeverity severity,
        String title
) {
    public ScoringFindingItem {
        if (severity == null) {
            severity = FindingSeverity.UNKNOWN;
        }
    }

    public static ScoringFindingItem of(FindingSeverity severity) {
        return new ScoringFindingItem(null, severity, null);
    }

    public static ScoringFindingItem of(FindingType type, FindingSeverity severity) {
        return new ScoringFindingItem(type, severity, null);
    }
}
