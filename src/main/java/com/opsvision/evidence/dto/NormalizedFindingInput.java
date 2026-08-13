package com.opsvision.evidence.dto;

import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Scanner-agnostic finding payload used during CI/CD evidence ingestion.
 */
public record NormalizedFindingInput(
        @NotNull FindingType findingType,
        FindingSeverity severity,
        String ruleId,
        @NotBlank String title,
        String description,
        String filePath,
        Integer lineNumber,
        String packageName,
        String installedVersion,
        String fixedVersion,
        String externalId
) {
    public NormalizedFindingInput {
        if (severity == null) {
            severity = FindingSeverity.UNKNOWN;
        }
    }
}
