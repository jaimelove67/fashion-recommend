CREATE TABLE admin_ai_model_settings (
    capability VARCHAR(40) PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL,
    api_key_ciphertext VARCHAR(4096),
    updated_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_admin_ai_model_provider
        CHECK (provider IN ('DASHSCOPE', 'OPENAI')),
    CONSTRAINT chk_admin_image_generation_provider
        CHECK (capability <> 'DAILY_IMAGE_GENERATION' OR provider = 'DASHSCOPE')
);
