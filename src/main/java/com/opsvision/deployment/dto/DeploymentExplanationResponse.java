package com.opsvision.deployment.dto;

import java.util.List;

/**
 * API DTO for AI-generated deployment risk explanation (on-demand, not persisted).
 */
public record DeploymentExplanationResponse(
        Long deploymentId,
        String summary,
        List<String> concerns,
        List<String> remediations,
        String provider,
        String model,
        boolean available
) {
}
