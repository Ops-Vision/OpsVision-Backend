package com.opsvision.incident.mapper;

import com.opsvision.incident.dto.IncidentResponse;
import com.opsvision.incident.dto.IncidentTimelineEntryResponse;
import com.opsvision.incident.dto.ProbableCauseResponse;
import com.opsvision.incident.dto.RootCauseAnalysisResponse;
import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.entity.IncidentTimelineEntry;
import com.opsvision.incident.model.ProbableCause;
import com.opsvision.incident.model.RootCauseAnalysisResult;
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

    public RootCauseAnalysisResponse toRcaResponse(RootCauseAnalysisResult result) {
        List<ProbableCauseResponse> causes = result.probableCauses().stream()
                .map(this::toProbableCauseResponse)
                .toList();
        return new RootCauseAnalysisResponse(
                result.incidentId(),
                result.analyzedAt(),
                result.method(),
                result.summary(),
                causes,
                result.limitations()
        );
    }

    public ProbableCauseResponse toProbableCauseResponse(ProbableCause cause) {
        return new ProbableCauseResponse(
                cause.cause(),
                cause.confidence(),
                cause.category(),
                cause.evidence()
        );
    }
}
