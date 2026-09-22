package com.fashion.recommendation.ai;

import java.time.Instant;

/** Runtime-only configuration. The API key must never be serialized or logged. */
public record AiModelRuntimeConfig(
        AiModelCapability capability,
        AiModelProvider provider,
        String model,
        String apiKey,
        boolean enabled,
        String endpoint,
        String taskEndpoint,
        String credentialSource,
        boolean credentialConfigured,
        boolean encryptionAvailable,
        boolean managedOverride,
        Instant updatedAt) {

    @Override
    public String toString() {
        return "AiModelRuntimeConfig[capability=" + capability
                + ", provider=" + provider
                + ", model=" + model
                + ", enabled=" + enabled
                + ", apiKey=<redacted>]";
    }
}
