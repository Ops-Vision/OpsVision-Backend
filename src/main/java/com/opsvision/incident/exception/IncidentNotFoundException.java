package com.opsvision.incident.exception;

/**
 * Raised when an incident id does not exist.
 */
public class IncidentNotFoundException extends RuntimeException {

    private final Long incidentId;

    public IncidentNotFoundException(Long incidentId) {
        super("Incident not found: " + incidentId);
        this.incidentId = incidentId;
    }

    public Long getIncidentId() {
        return incidentId;
    }
}
