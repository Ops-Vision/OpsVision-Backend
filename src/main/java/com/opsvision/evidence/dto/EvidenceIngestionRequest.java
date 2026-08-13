package com.opsvision.evidence.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request to attach one or more normalized evidence items to an existing deployment.
 */
public record EvidenceIngestionRequest(
        @NotNull Long deploymentId,
        @NotEmpty @Valid List<NormalizedEvidenceInput> evidence
) {
    public EvidenceIngestionRequest {
        if (evidence != null) {
            evidence = List.copyOf(evidence);
        }
    }
}
