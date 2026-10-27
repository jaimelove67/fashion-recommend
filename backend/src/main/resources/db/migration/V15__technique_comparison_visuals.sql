-- A single durable lock serializes reservations across instances and calendar months.
CREATE TABLE technique_visual_budget (id INTEGER PRIMARY KEY CHECK (id=1));
INSERT INTO technique_visual_budget(id) VALUES (1);
CREATE TABLE technique_visual_attempts (
    id VARCHAR(36) PRIMARY KEY,
    month_key VARCHAR(7) NOT NULL,
    technique_id VARCHAR(160) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    outcome VARCHAR(24) NOT NULL,
    UNIQUE(month_key,technique_id)
);
CREATE INDEX idx_technique_visual_month ON technique_visual_attempts(month_key);
CREATE TABLE technique_comparison_visuals (
    technique_id VARCHAR(160) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    asset_id VARCHAR(36) UNIQUE,
    image_content BYTEA,
    content_type VARCHAR(50),
    PRIMARY KEY(technique_id,fingerprint)
);
