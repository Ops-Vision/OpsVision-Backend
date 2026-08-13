-- Baseline schema placeholder for OpsVision backend foundation.
-- Domain tables will be introduced in subsequent migrations.
CREATE TABLE IF NOT EXISTS app_meta (
    id          BIGSERIAL PRIMARY KEY,
    meta_key    VARCHAR(128) NOT NULL UNIQUE,
    meta_value  VARCHAR(512) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO app_meta (meta_key, meta_value)
VALUES ('schema_initialized', 'true')
ON CONFLICT (meta_key) DO NOTHING;
