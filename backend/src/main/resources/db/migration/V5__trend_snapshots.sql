CREATE TABLE trend_contents (
    id VARCHAR(160) PRIMARY KEY,
    platform VARCHAR(40) NOT NULL,
    source_id VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    hidden BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_trend_window ON trend_contents(published_at, platform);
CREATE TABLE trend_snapshots (
    content_id VARCHAR(160) NOT NULL REFERENCES trend_contents(id) ON DELETE CASCADE,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    likes BIGINT,
    favorites BIGINT,
    comments BIGINT,
    reposts BIGINT,
    PRIMARY KEY (content_id, observed_at)
);
CREATE TABLE trend_source_status (
    id VARCHAR(80) PRIMARY KEY,
    last_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_success_at TIMESTAMP WITH TIME ZONE,
    state VARCHAR(30) NOT NULL,
    message VARCHAR(300) NOT NULL,
    item_count INTEGER NOT NULL DEFAULT 0
);
