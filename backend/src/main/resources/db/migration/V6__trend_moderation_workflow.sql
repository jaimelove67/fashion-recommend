ALTER TABLE trend_contents
    ADD COLUMN moderation_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_AI';
ALTER TABLE trend_contents
    ADD COLUMN ai_decision VARCHAR(16);
ALTER TABLE trend_contents
    ADD COLUMN ai_risk_level VARCHAR(16);
ALTER TABLE trend_contents
    ADD COLUMN ai_reason VARCHAR(500);
ALTER TABLE trend_contents
    ADD COLUMN ai_model VARCHAR(80);
ALTER TABLE trend_contents
    ADD COLUMN ai_provider_call_id VARCHAR(160);
ALTER TABLE trend_contents
    ADD COLUMN ai_prompt_version VARCHAR(80);
ALTER TABLE trend_contents
    ADD COLUMN ai_reviewed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE trend_contents
    ADD COLUMN reviewed_by VARCHAR(32);
ALTER TABLE trend_contents
    ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE trend_contents
    ADD COLUMN review_note VARCHAR(500);

ALTER TABLE trend_contents
    ADD CONSTRAINT chk_trend_moderation_status
    CHECK (moderation_status IN ('PENDING_AI', 'AI_FAILED', 'PENDING_HUMAN', 'APPROVED', 'REJECTED'));
ALTER TABLE trend_contents
    ADD CONSTRAINT chk_trend_ai_decision
    CHECK (ai_decision IS NULL OR ai_decision IN ('PASS', 'REVIEW', 'REJECT', 'ERROR'));
ALTER TABLE trend_contents
    ADD CONSTRAINT chk_trend_ai_risk_level
    CHECK (ai_risk_level IS NULL OR ai_risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'UNKNOWN'));

CREATE INDEX idx_trend_moderation_queue
    ON trend_contents (moderation_status, published_at, id);
