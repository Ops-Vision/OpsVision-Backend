package com.opsvision.deployment.dto;

import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;

import java.time.Instant;

public record FindingResponse(
        Long id,
        Long deploymentId,
        Long evidenceId,
        FindingType findingType,
        FindingSeverity severity,
        String ruleId,
        String title,
        String description,
        String filePath,
        Integer lineNumber,
        String packageName,
        String installedVersion,
        String fixedVersion,
        String externalId,
        Instant createdAt
) {
}
