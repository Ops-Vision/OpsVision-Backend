package com.opsvision.incident.entity;

import com.opsvision.incident.model.TimelineEntryType;
import com.opsvision.incident.model.TimelineSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Ordered timeline event correlated into an incident.
 */
@Entity
@Table(name = "incident_timeline_entry")
public class IncidentTimelineEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 64)
    private TimelineEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private TimelineSource source;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(length = 4000)
    private String detail;

    @Column(name = "signal_key", length = 255)
    private String signalKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IncidentTimelineEntry() {
    }

    public IncidentTimelineEntry(
            Instant occurredAt,
            TimelineEntryType entryType,
            TimelineSource source,
            String title,
            String detail,
            String signalKey,
            int sortOrder
    ) {
        this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
        this.entryType = entryType != null ? entryType : TimelineEntryType.OTHER;
        this.source = source != null ? source : TimelineSource.OTHER;
        this.title = title;
        this.detail = detail;
        this.signalKey = signalKey;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public void setIncident(Incident incident) {
        this.incident = incident;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public TimelineEntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(TimelineEntryType entryType) {
        this.entryType = entryType;
    }

    public TimelineSource getSource() {
        return source;
    }

    public void setSource(TimelineSource source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getSignalKey() {
        return signalKey;
    }

    public void setSignalKey(String signalKey) {
        this.signalKey = signalKey;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IncidentTimelineEntry that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
