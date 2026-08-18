package com.opsvision.deployment.dto;

import com.opsvision.deployment.model.DeploymentStatus;

import java.time.Instant;

public record DeploymentResponse(
        Long id,
        RepositorySummaryDto repository,
        String commitSha,
        String branch,
        String environment,
        DeploymentStatus status,
        String workflowName,
        Long workflowRunId,
        String workflowRunUrl,
        Instant deployedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
