package com.fashion.recommendation.ai;

import java.time.Instant;

public record AiModelSettingRow(
        AiModelCapability capability,
        AiModelProvider provider,
        String model,
        boolean enabled,
        String encryptedApiKey,
        String updatedBy,
        Instant updatedAt) {

    @Override
    public String toString() {
        return "AiModelSettingRow[capability=" + capability
                + ", provider=" + provider
                + ", model=" + model
                + ", enabled=" + enabled
                + ", encryptedApiKey=<redacted>]";
    }
}
