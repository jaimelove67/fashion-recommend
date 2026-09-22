package com.fashion.recommendation.admin;

import com.fashion.recommendation.ai.AiModelProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminAiModelConfigUpdateRequest(
        @NotNull AiModelProvider provider,
        @NotBlank @Size(max = 120) String model,
        @NotNull Boolean enabled,
        @Size(max = 2048) String apiKey,
        boolean clearApiKey) {

    @Override
    public String toString() {
        return "AdminAiModelConfigUpdateRequest[provider=" + provider
                + ", model=" + model
                + ", enabled=" + enabled
                + ", apiKey=<redacted>"
                + ", clearApiKey=" + clearApiKey + "]";
    }
}
