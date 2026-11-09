CREATE TABLE recommendation_visual_usage (
    token VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX visual_usage_user_date ON recommendation_visual_usage(user_id, reserved_at);
