package com.opsvision.incident.model;

/**
 * Origin of a timeline entry.
 */
public enum TimelineSource {
    DEPLOYMENT,
    KUBERNETES,
    PROMETHEUS,
    SYSTEM,
    USER,
    OTHER
}
