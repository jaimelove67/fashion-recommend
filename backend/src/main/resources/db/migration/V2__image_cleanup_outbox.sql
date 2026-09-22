CREATE TABLE IF NOT EXISTS image_cleanup_tasks (
    object_key VARCHAR(300) PRIMARY KEY,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_image_cleanup_tasks_due
    ON image_cleanup_tasks (next_attempt_at, object_key);
