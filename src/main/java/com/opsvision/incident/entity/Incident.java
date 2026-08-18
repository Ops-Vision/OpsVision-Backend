package com.opsvision.incident.entity;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.incident.model.IncidentSeverity;
import com.opsvision.incident.model.IncidentStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A post-deployment incident correlated with telemetry signals and optional deployment.
 */
@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id")
    private Deployment deployment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncidentSeverity severity = IncidentSeverity.MEDIUM;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(length = 4000)
    private String summary;

    @Column(length = 255)
    private String namespace;

    @Column(name = "workload_name", length = 255)
    private String workloadName;

    @Column(name = "commit_sha", length = 64)
    private String commitSha;

    @Column(length = 128)
    private String environment;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "github_issue_number")
    private Long githubIssueNumber;

    @Column(name = "github_issue_url", length = 512)
    private String githubIssueUrl;

    @Column(name = "github_issue_created_at")
    private Instant githubIssueCreatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt ASC, sortOrder ASC, id ASC")
    private List<IncidentTimelineEntry> timelineEntries = new ArrayList<>();

    protected Incident() {
    }

    public Incident(
            String title,
            IncidentSeverity severity,
            Instant detectedAt
    ) {
        this.title = title;
        this.severity = severity != null ? severity : IncidentSeverity.MEDIUM;
        this.detectedAt = detectedAt != null ? detectedAt : Instant.now();
        this.status = IncidentStatus.OPEN;
        this.startedAt = this.detectedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (detectedAt == null) {
            detectedAt = now;
        }
        if (startedAt == null) {
            startedAt = detectedAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addTimelineEntry(IncidentTimelineEntry entry) {
        timelineEntries.add(entry);
        entry.setIncident(this);
    }

    public void clearTimeline() {
        for (IncidentTimelineEntry entry : timelineEntries) {
            entry.setIncident(null);
        }
        timelineEntries.clear();
    }

    public Long getId() {
        return id;
    }

    public Deployment getDeployment() {
        return deployment;
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(IncidentSeverity severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getWorkloadName() {
        return workloadName;
    }

    public void setWorkloadName(String workloadName) {
        this.workloadName = workloadName;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Long getGithubIssueNumber() {
        return githubIssueNumber;
    }

    public void setGithubIssueNumber(Long githubIssueNumber) {
        this.githubIssueNumber = githubIssueNumber;
    }

    public String getGithubIssueUrl() {
        return githubIssueUrl;
    }

    public void setGithubIssueUrl(String githubIssueUrl) {
        this.githubIssueUrl = githubIssueUrl;
    }

    public Instant getGithubIssueCreatedAt() {
        return githubIssueCreatedAt;
    }

    public void setGithubIssueCreatedAt(Instant githubIssueCreatedAt) {
        this.githubIssueCreatedAt = githubIssueCreatedAt;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<IncidentTimelineEntry> getTimelineEntries() {
        return timelineEntries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Incident that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
