package com.opsvision.deployment.dto;

import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;

import java.math.BigDecimal;
import java.time.Instant;

public record EvidenceResponse(
        Long id,
        Long deploymentId,
        EvidenceType evidenceType,
        EvidenceStatus status,
        String source,
        String summary,
        BigDecimal metricValue,
        String metricUnit,
        String rawReference,
        Instant collectedAt,
        Instant createdAt
) {
}
