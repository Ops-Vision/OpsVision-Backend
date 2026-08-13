package com.opsvision.evidence.dto;

import java.util.List;

/**
 * Outcome of persisting normalized evidence against a deployment.
 */
public record EvidenceIngestionResult(
        Long deploymentId,
        int evidenceCount,
        int findingCount,
        List<Long> evidenceIds,
        List<Long> findingIds
) {
    public EvidenceIngestionResult {
        if (evidenceIds != null) {
            evidenceIds = List.copyOf(evidenceIds);
        } else {
            evidenceIds = List.of();
        }
        if (findingIds != null) {
            findingIds = List.copyOf(findingIds);
        } else {
            findingIds = List.of();
        }
    }
}
