package com.opsvision.incident.model;

/**
 * Lifecycle status of a detected incident.
 */
public enum IncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    INVESTIGATING,
    RESOLVED,
    CLOSED
}
