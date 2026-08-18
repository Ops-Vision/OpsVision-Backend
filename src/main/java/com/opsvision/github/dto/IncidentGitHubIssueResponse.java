package com.opsvision.github.dto;

import java.time.Instant;

/**
 * REST response after creating or reusing a GitHub issue for an incident.
 */
public record IncidentGitHubIssueResponse(
        Long incidentId,
        Integer issueNumber,
        String issueUrl,
        String title,
        String state,
        boolean created,
        boolean duplicatePrevented,
        Instant linkedAt
) {
}
