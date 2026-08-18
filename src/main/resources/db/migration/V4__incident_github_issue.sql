-- Track GitHub issues created for incidents (duplicate prevention)

ALTER TABLE incident
    ADD COLUMN github_issue_number BIGINT,
    ADD COLUMN github_issue_url    VARCHAR(512),
    ADD COLUMN github_issue_created_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uk_incident_github_issue_number
    ON incident (github_issue_number)
    WHERE github_issue_number IS NOT NULL;
