package com.opsvision.incident.dto;

/**
 * Result of a detection run: either a new/updated incident or a no-incident outcome.
 */
public record IncidentDetectionResponse(
        boolean incidentDetected,
        IncidentResponse incident,
        String message
) {
    public static IncidentDetectionResponse none(String message) {
        return new IncidentDetectionResponse(false, null, message);
    }

    public static IncidentDetectionResponse of(IncidentResponse incident) {
        return new IncidentDetectionResponse(true, incident, "Incident detected");
    }
}
