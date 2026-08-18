-- Incident detection and timeline domain

CREATE TABLE incident (
    id                BIGSERIAL PRIMARY KEY,
    deployment_id     BIGINT       REFERENCES deployment (id) ON DELETE SET NULL,
    status            VARCHAR(32)  NOT NULL,
    severity          VARCHAR(32)  NOT NULL,
    title             VARCHAR(512) NOT NULL,
    summary           VARCHAR(4000),
    namespace         VARCHAR(255),
    workload_name     VARCHAR(255),
    commit_sha        VARCHAR(64),
    environment       VARCHAR(128),
    detected_at       TIMESTAMPTZ  NOT NULL,
    started_at        TIMESTAMPTZ,
    resolved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_incident_status CHECK (
        status IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED', 'CLOSED')
    ),
    CONSTRAINT chk_incident_severity CHECK (
        severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')
    )
);

CREATE INDEX idx_incident_deployment_id ON incident (deployment_id);
CREATE INDEX idx_incident_status ON incident (status);
CREATE INDEX idx_incident_detected_at ON incident (detected_at DESC);
CREATE INDEX idx_incident_namespace_workload ON incident (namespace, workload_name);

CREATE TABLE incident_timeline_entry (
    id              BIGSERIAL PRIMARY KEY,
    incident_id     BIGINT        NOT NULL REFERENCES incident (id) ON DELETE CASCADE,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    entry_type      VARCHAR(64)   NOT NULL,
    source          VARCHAR(64)   NOT NULL,
    title           VARCHAR(512)  NOT NULL,
    detail          VARCHAR(4000),
    signal_key      VARCHAR(255),
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_timeline_entry_type CHECK (
        entry_type IN (
            'DEPLOYMENT', 'METRIC', 'POD', 'KUBERNETES_EVENT',
            'WORKLOAD', 'SIGNAL', 'NOTE', 'STATUS_CHANGE', 'OTHER'
        )
    ),
    CONSTRAINT chk_timeline_source CHECK (
        source IN ('DEPLOYMENT', 'KUBERNETES', 'PROMETHEUS', 'SYSTEM', 'USER', 'OTHER')
    )
);

CREATE INDEX idx_incident_timeline_incident_id ON incident_timeline_entry (incident_id);
CREATE INDEX idx_incident_timeline_occurred_at ON incident_timeline_entry (incident_id, occurred_at, sort_order);
