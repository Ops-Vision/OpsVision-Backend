package com.opsvision.evidence.entity;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Normalized CI/CD or scanner evidence associated with a deployment.
 * Scanner-specific payload details stay out of the deployment row.
 */
@Entity
@Table(name = "deployment_evidence")
public class DeploymentEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 64)
    private EvidenceType evidenceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvidenceStatus status = EvidenceStatus.UNKNOWN;

    @Column(nullable = false, length = 255)
    private String source;

    @Column(length = 1024)
    private String summary;

    /**
     * Optional numeric metric (e.g. coverage percentage 0–100).
     */
    @Column(name = "metric_value", precision = 10, scale = 4)
    private BigDecimal metricValue;

    @Column(name = "metric_unit", length = 32)
    private String metricUnit;

    @Column(name = "raw_reference", length = 1024)
    private String rawReference;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DeploymentEvidence() {
    }

    public DeploymentEvidence(
            EvidenceType evidenceType,
            EvidenceStatus status,
            String source,
            String summary
    ) {
        this.evidenceType = evidenceType;
        this.status = status != null ? status : EvidenceStatus.UNKNOWN;
        this.source = source;
        this.summary = summary;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (collectedAt == null) {
            collectedAt = createdAt;
        }
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

    public EvidenceType getEvidenceType() {
        return evidenceType;
    }

    public void setEvidenceType(EvidenceType evidenceType) {
        this.evidenceType = evidenceType;
    }

    public EvidenceStatus getStatus() {
        return status;
    }

    public void setStatus(EvidenceStatus status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public BigDecimal getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(BigDecimal metricValue) {
        this.metricValue = metricValue;
    }

    public String getMetricUnit() {
        return metricUnit;
    }

    public void setMetricUnit(String metricUnit) {
        this.metricUnit = metricUnit;
    }

    public String getRawReference() {
        return rawReference;
    }

    public void setRawReference(String rawReference) {
        this.rawReference = rawReference;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(Instant collectedAt) {
        this.collectedAt = collectedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeploymentEvidence that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
