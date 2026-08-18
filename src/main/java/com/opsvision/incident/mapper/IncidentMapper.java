package com.opsvision.incident.mapper;

import com.opsvision.incident.dto.IncidentResponse;
import com.opsvision.incident.dto.IncidentTimelineEntryResponse;
import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.entity.IncidentTimelineEntry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IncidentMapper {

    public IncidentResponse toResponse(Incident incident) {
        Long deploymentId = incident.getDeployment() != null ? incident.getDeployment().getId() : null;
        List<IncidentTimelineEntryResponse> timeline = incident.getTimelineEntries().stream()
                .map(this::toTimelineResponse)
                .toList();
        return new IncidentResponse(
                incident.getId(),
                deploymentId,
                incident.getStatus(),
                incident.getSeverity(),
                incident.getTitle(),
                incident.getSummary(),
                incident.getNamespace(),
                incident.getWorkloadName(),
                incident.getCommitSha(),
                incident.getEnvironment(),
                incident.getDetectedAt(),
                incident.getStartedAt(),
                incident.getResolvedAt(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                timeline
        );
    }

    public IncidentTimelineEntryResponse toTimelineResponse(IncidentTimelineEntry entry) {
        return new IncidentTimelineEntryResponse(
                entry.getId(),
                entry.getOccurredAt(),
                entry.getEntryType(),
                entry.getSource(),
                entry.getTitle(),
                entry.getDetail(),
                entry.getSignalKey(),
                entry.getSortOrder()
        );
    }
}
