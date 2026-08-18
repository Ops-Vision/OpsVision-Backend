package com.opsvision.incident.dto;

import java.util.List;

public record ProbableCauseResponse(
        String cause,
        double confidence,
        String category,
        List<String> evidence
) {
}
