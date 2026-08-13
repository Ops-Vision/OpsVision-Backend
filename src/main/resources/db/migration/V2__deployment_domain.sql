-- Core deployment analysis domain model

CREATE TABLE project_repository (
    id              BIGSERIAL PRIMARY KEY,
    owner           VARCHAR(200)  NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    full_name       VARCHAR(401)  NOT NULL,
    default_branch  VARCHAR(255),
    url             VARCHAR(512),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_project_repository_owner_name UNIQUE (owner, name)
);

CREATE INDEX idx_project_repository_full_name ON project_repository (full_name);

CREATE TABLE deployment (
    id                BIGSERIAL PRIMARY KEY,
    repository_id     BIGINT       NOT NULL REFERENCES project_repository (id) ON DELETE CASCADE,
    commit_sha        VARCHAR(64)  NOT NULL,
    branch            VARCHAR(255) NOT NULL,
    environment       VARCHAR(128) NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    workflow_name     VARCHAR(255),
    workflow_run_id   BIGINT,
    workflow_run_url  VARCHAR(1024),
    deployed_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_deployment_repo_commit_env UNIQUE (repository_id, commit_sha, environment),
    CONSTRAINT chk_deployment_status CHECK (
        status IN ('PENDING', 'ANALYZING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    )
);

CREATE INDEX idx_deployment_repository_id ON deployment (repository_id);
CREATE INDEX idx_deployment_status ON deployment (status);
CREATE INDEX idx_deployment_created_at ON deployment (created_at DESC);

CREATE TABLE deployment_evidence (
    id              BIGSERIAL PRIMARY KEY,
    deployment_id   BIGINT         NOT NULL REFERENCES deployment (id) ON DELETE CASCADE,
    evidence_type   VARCHAR(64)    NOT NULL,
    status          VARCHAR(32)    NOT NULL,
    source          VARCHAR(255)   NOT NULL,
    summary         VARCHAR(1024),
    metric_value    NUMERIC(10, 4),
    metric_unit     VARCHAR(32),
    raw_reference   VARCHAR(1024),
    collected_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_evidence_type CHECK (
        evidence_type IN (
            'BUILD', 'TEST', 'CODE_COVERAGE', 'STATIC_ANALYSIS',
            'DEPENDENCY_SCAN', 'CONTAINER_SCAN', 'WORKFLOW', 'OTHER'
        )
    ),
    CONSTRAINT chk_evidence_status CHECK (
        status IN ('PASSED', 'FAILED', 'WARNING', 'SKIPPED', 'UNKNOWN')
    )
);

CREATE INDEX idx_deployment_evidence_deployment_id ON deployment_evidence (deployment_id);
CREATE INDEX idx_deployment_evidence_type ON deployment_evidence (evidence_type);

CREATE TABLE finding (
    id                 BIGSERIAL PRIMARY KEY,
    deployment_id      BIGINT        NOT NULL REFERENCES deployment (id) ON DELETE CASCADE,
    evidence_id        BIGINT        REFERENCES deployment_evidence (id) ON DELETE SET NULL,
    finding_type       VARCHAR(64)   NOT NULL,
    severity           VARCHAR(32)   NOT NULL,
    rule_id            VARCHAR(255),
    title              VARCHAR(512)  NOT NULL,
    description        VARCHAR(4000),
    file_path          VARCHAR(1024),
    line_number        INTEGER,
    package_name       VARCHAR(512),
    installed_version  VARCHAR(128),
    fixed_version      VARCHAR(128),
    external_id        VARCHAR(255),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_finding_type CHECK (
        finding_type IN (
            'SECURITY_VULNERABILITY', 'STATIC_ANALYSIS', 'DEPENDENCY',
            'CONTAINER', 'COVERAGE', 'TEST_FAILURE', 'OTHER'
        )
    ),
    CONSTRAINT chk_finding_severity CHECK (
        severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO', 'UNKNOWN')
    )
);

CREATE INDEX idx_finding_deployment_id ON finding (deployment_id);
CREATE INDEX idx_finding_evidence_id ON finding (evidence_id);
CREATE INDEX idx_finding_severity ON finding (severity);
