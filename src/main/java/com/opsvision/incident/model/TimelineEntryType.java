package com.opsvision.incident.model;

/**
 * Kind of event on an incident timeline.
 */
public enum TimelineEntryType {
    DEPLOYMENT,
    METRIC,
    POD,
    KUBERNETES_EVENT,
    WORKLOAD,
    SIGNAL,
    NOTE,
    STATUS_CHANGE,
    OTHER
}
