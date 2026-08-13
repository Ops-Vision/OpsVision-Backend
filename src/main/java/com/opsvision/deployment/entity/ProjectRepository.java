package com.opsvision.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

/**
 * A source-control repository (project) tracked by OpsVision.
 */
@Entity
@Table(
        name = "project_repository",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_repository_owner_name",
                columnNames = {"owner", "name"}
        )
)
public class ProjectRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String owner;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "full_name", nullable = false, length = 401)
    private String fullName;

    @Column(name = "default_branch", length = 255)
    private String defaultBranch;

    @Column(length = 512)
    private String url;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectRepository() {
    }

    public ProjectRepository(String owner, String name, String defaultBranch, String url) {
        this.owner = owner;
        this.name = name;
        this.fullName = owner + "/" + name;
        this.defaultBranch = defaultBranch;
        this.url = url;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (fullName == null && owner != null && name != null) {
            fullName = owner + "/" + name;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        if (owner != null && name != null) {
            fullName = owner + "/" + name;
        }
    }

    public Long getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProjectRepository that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
