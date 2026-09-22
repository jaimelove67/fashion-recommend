package com.fashion.recommendation.admin;

import java.time.Instant;

public record AdminAiModelConfigView(
        String capability,
        String label,
        String provider,
        String providerLabel,
        String model,
        boolean enabled,
        boolean environmentEnabled,
        boolean effectiveEnabled,
        String credentialSource,
        boolean credentialConfigured,
        boolean encryptionAvailable,
        boolean managedOverride,
        Instant updatedAt) {
}
