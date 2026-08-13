package com.opsvision.deployment.entity;

import com.opsvision.deployment.model.DeploymentStatus;
import com.opsvision.evidence.entity.DeploymentEvidence;
import com.opsvision.evidence.entity.Finding;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An analyzed deployment attempt tied to a repository commit and environment.
 */
@Entity
@Table(
        name = "deployment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_deployment_repo_commit_env",
                columnNames = {"repository_id", "commit_sha", "environment"}
        )
)
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private ProjectRepository repository;

    @Column(name = "commit_sha", nullable = false, length = 64)
    private String commitSha;

    @Column(nullable = false, length = 255)
    private String branch;

    @Column(nullable = false, length = 128)
    private String environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeploymentStatus status = DeploymentStatus.PENDING;

    @Column(name = "workflow_name", length = 255)
    private String workflowName;

    @Column(name = "workflow_run_id")
    private Long workflowRunId;

    @Column(name = "workflow_run_url", length = 1024)
    private String workflowRunUrl;

    @Column(name = "deployed_at")
    private Instant deployedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "deployment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeploymentEvidence> evidenceItems = new ArrayList<>();

    @OneToMany(mappedBy = "deployment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Finding> findings = new ArrayList<>();

    protected Deployment() {
    }

    public Deployment(
            ProjectRepository repository,
            String commitSha,
            String branch,
            String environment,
            DeploymentStatus status
    ) {
        this.repository = repository;
        this.commitSha = commitSha;
        this.branch = branch;
        this.environment = environment;
        this.status = status != null ? status : DeploymentStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addEvidence(DeploymentEvidence evidence) {
        evidenceItems.add(evidence);
        evidence.setDeployment(this);
    }

    public void removeEvidence(DeploymentEvidence evidence) {
        evidenceItems.remove(evidence);
        evidence.setDeployment(null);
    }

    public void addFinding(Finding finding) {
        findings.add(finding);
        finding.setDeployment(this);
    }

    public void removeFinding(Finding finding) {
        findings.remove(finding);
        finding.setDeployment(null);
    }

    public Long getId() {
        return id;
    }

    public ProjectRepository getRepository() {
        return repository;
    }

    public void setRepository(ProjectRepository repository) {
        this.repository = repository;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public DeploymentStatus getStatus() {
        return status;
    }

    public void setStatus(DeploymentStatus status) {
        this.status = status;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public Long getWorkflowRunId() {
        return workflowRunId;
    }

    public void setWorkflowRunId(Long workflowRunId) {
        this.workflowRunId = workflowRunId;
    }

    public String getWorkflowRunUrl() {
        return workflowRunUrl;
    }

    public void setWorkflowRunUrl(String workflowRunUrl) {
        this.workflowRunUrl = workflowRunUrl;
    }

    public Instant getDeployedAt() {
        return deployedAt;
    }

    public void setDeployedAt(Instant deployedAt) {
        this.deployedAt = deployedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<DeploymentEvidence> getEvidenceItems() {
        return evidenceItems;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Deployment that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
