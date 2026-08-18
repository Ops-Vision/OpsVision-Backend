package com.opsvision.incident.dto;

import com.opsvision.incident.model.TimelineEntryType;
import com.opsvision.incident.model.TimelineSource;

import java.time.Instant;

public record IncidentTimelineEntryResponse(
        Long id,
        Instant occurredAt,
        TimelineEntryType entryType,
        TimelineSource source,
        String title,
        String detail,
        String signalKey,
        int sortOrder
) {
}
