package com.opsvision.deployment.dto;

import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request to create (or locate) a deployment and optionally ingest evidence then analyze.
 */
public record AnalyzeDeploymentRequest(
        @NotBlank @Size(max = 200) String owner,
        @NotBlank @Size(max = 200) String repository,
        @NotBlank @Size(max = 64) String commitSha,
        @NotBlank @Size(max = 255) String branch,
        @NotBlank @Size(max = 128) String environment,
        @Size(max = 255) String workflowName,
        Long workflowRunId,
        @Size(max = 1024) String workflowRunUrl,
        @Size(max = 512) String repositoryUrl,
        @Valid List<NormalizedEvidenceInput> evidence
) {
    public AnalyzeDeploymentRequest {
        if (evidence == null) {
            evidence = List.of();
        } else {
            evidence = List.copyOf(evidence);
        }
    }
}
