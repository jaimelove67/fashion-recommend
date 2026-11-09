CREATE TABLE recommendation_visuals (
    recommendation_id BIGINT NOT NULL REFERENCES recommendations(id) ON DELETE CASCADE,
    fingerprint VARCHAR(64) NOT NULL,
    asset_id VARCHAR(36) UNIQUE,
    image_content BYTEA,
    content_type VARCHAR(50),
    model VARCHAR(160),
    generation_token VARCHAR(36),
    lease_until TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (recommendation_id, fingerprint)
);
