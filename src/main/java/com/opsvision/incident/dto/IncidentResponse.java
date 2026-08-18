package com.opsvision.incident.dto;

import com.opsvision.incident.model.IncidentSeverity;
import com.opsvision.incident.model.IncidentStatus;

import java.time.Instant;
import java.util.List;

public record IncidentResponse(
        Long id,
        Long deploymentId,
        IncidentStatus status,
        IncidentSeverity severity,
        String title,
        String summary,
        String namespace,
        String workloadName,
        String commitSha,
        String environment,
        Instant detectedAt,
        Instant startedAt,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt,
        List<IncidentTimelineEntryResponse> timeline
) {
}
