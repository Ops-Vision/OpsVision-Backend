-- Query-path indexes for list/filter hot paths (idempotent hardening)

CREATE INDEX IF NOT EXISTS idx_deployment_repo_created_at
    ON deployment (repository_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_deployment_commit_sha
    ON deployment (commit_sha);

CREATE INDEX IF NOT EXISTS idx_finding_deployment_severity
    ON finding (deployment_id, severity);

CREATE INDEX IF NOT EXISTS idx_incident_status_detected_at
    ON incident (status, detected_at DESC);

CREATE INDEX IF NOT EXISTS idx_incident_commit_sha
    ON incident (commit_sha)
    WHERE commit_sha IS NOT NULL;
