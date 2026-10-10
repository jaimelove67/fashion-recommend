ALTER TABLE style_profiles ADD COLUMN avoid_preferences TEXT NOT NULL DEFAULT '[]';
ALTER TABLE style_profiles ADD COLUMN preferences_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing choices are preserved. Their origin was not recorded, so the user can review and confirm them.
