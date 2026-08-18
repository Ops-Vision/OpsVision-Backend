package com.opsvision.incident.dto;

import java.time.Instant;
import java.util.List;

public record RootCauseAnalysisResponse(
        Long incidentId,
        Instant analyzedAt,
        String method,
        String summary,
        List<ProbableCauseResponse> probableCauses,
        List<String> limitations
) {
}
