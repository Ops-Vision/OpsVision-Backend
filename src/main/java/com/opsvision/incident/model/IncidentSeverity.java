package com.opsvision.incident.model;

/**
 * Severity of a detected incident (derived from signals, not LLM).
 */
public enum IncidentSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}
