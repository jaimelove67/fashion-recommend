-- Recommendation generation audit metadata.
-- Nullable so legacy/rule-derived rows stay honest: only rows that actually
-- received a successful provider response carry provider_call_id / token usage.
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS model_name VARCHAR(160);
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(64);
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS provider_call_id VARCHAR(200);
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS prompt_tokens INT;
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS completion_tokens INT;
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS total_tokens INT;
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS generation_latency_ms BIGINT;
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS fallback_reason VARCHAR(64);

ALTER TABLE recommendations ADD CONSTRAINT chk_recommendations_prompt_tokens
    CHECK (prompt_tokens IS NULL OR prompt_tokens >= 0);
ALTER TABLE recommendations ADD CONSTRAINT chk_recommendations_completion_tokens
    CHECK (completion_tokens IS NULL OR completion_tokens >= 0);
ALTER TABLE recommendations ADD CONSTRAINT chk_recommendations_total_tokens
    CHECK (total_tokens IS NULL OR total_tokens >= 0);
ALTER TABLE recommendations ADD CONSTRAINT chk_recommendations_latency_ms
    CHECK (generation_latency_ms IS NULL OR generation_latency_ms >= 0);