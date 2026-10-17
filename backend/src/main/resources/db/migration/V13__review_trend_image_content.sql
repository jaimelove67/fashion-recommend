ALTER TABLE trend_contents ADD COLUMN reviewed_content_hash VARCHAR(64);
ALTER TABLE trend_contents ALTER COLUMN moderation_status SET DEFAULT 'PENDING_HUMAN';

-- Earlier approvals did not enforce the current image-content policy. Preserve
-- the payload and review history, but require an explicit review of this version.
UPDATE trend_contents SET moderation_status = 'PENDING_HUMAN'
WHERE moderation_status IN ('PENDING_AI', 'AI_FAILED', 'APPROVED');
