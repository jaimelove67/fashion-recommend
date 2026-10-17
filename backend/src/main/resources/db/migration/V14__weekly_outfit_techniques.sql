-- Publication and scheduling state survive restarts. Article bodies remain transient.
CREATE TABLE outfit_techniques (
    id VARCHAR(160) PRIMARY KEY,
    normalized_title VARCHAR(240) NOT NULL UNIQUE,
    source_set_hash VARCHAR(64) NOT NULL UNIQUE,
    payload TEXT NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_outfit_techniques_published ON outfit_techniques(published_at);

CREATE TABLE outfit_technique_attempts (
    id VARCHAR(36) PRIMARY KEY,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    outcome VARCHAR(24) NOT NULL,
    error_code VARCHAR(40),
    quality_score INTEGER,
    model_name VARCHAR(160),
    provider_call_id VARCHAR(256)
);
CREATE INDEX idx_outfit_technique_attempts_started ON outfit_technique_attempts(started_at);

CREATE TABLE outfit_technique_schedule (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(40),
    lease_token VARCHAR(36),
    lease_until TIMESTAMP WITH TIME ZONE
);
INSERT INTO outfit_technique_schedule(id) VALUES (1);
