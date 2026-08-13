package com.opsvision.evidence.entity;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;
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
 * Individual finding (vulnerability, rule hit, etc.) linked to a deployment
 * and optionally to a parent evidence row.
 */
@Entity
@Table(name = "finding")
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_id")
    private DeploymentEvidence evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "finding_type", nullable = false, length = 64)
    private FindingType findingType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FindingSeverity severity = FindingSeverity.UNKNOWN;

    @Column(name = "rule_id", length = 255)
    private String ruleId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(length = 4000)
    private String description;

    @Column(name = "file_path", length = 1024)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "package_name", length = 512)
    private String packageName;

    @Column(name = "installed_version", length = 128)
    private String installedVersion;

    @Column(name = "fixed_version", length = 128)
    private String fixedVersion;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Finding() {
    }

    public Finding(
            FindingType findingType,
            FindingSeverity severity,
            String title,
            String description
    ) {
        this.findingType = findingType;
        this.severity = severity != null ? severity : FindingSeverity.UNKNOWN;
        this.title = title;
        this.description = description;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
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

    public DeploymentEvidence getEvidence() {
        return evidence;
    }

    public void setEvidence(DeploymentEvidence evidence) {
        this.evidence = evidence;
    }

    public FindingType getFindingType() {
        return findingType;
    }

    public void setFindingType(FindingType findingType) {
        this.findingType = findingType;
    }

    public FindingSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(FindingSeverity severity) {
        this.severity = severity;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getInstalledVersion() {
        return installedVersion;
    }

    public void setInstalledVersion(String installedVersion) {
        this.installedVersion = installedVersion;
    }

    public String getFixedVersion() {
        return fixedVersion;
    }

    public void setFixedVersion(String fixedVersion) {
        this.fixedVersion = fixedVersion;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Finding that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
