package com.opsvision.incident.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A single deterministic detection signal used to decide whether to open an incident.
 */
public record DetectedSignal(
        String key,
        TimelineEntryType entryType,
        TimelineSource source,
        IncidentSeverity severity,
        Instant occurredAt,
        String title,
        String detail
) {
    public DetectedSignal {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(entryType, "entryType");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(title, "title");
        if (detail == null) {
            detail = "";
        }
    }
}
