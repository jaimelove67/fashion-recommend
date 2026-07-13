package com.fashion.recommendation.style;

import java.time.Instant;
import java.util.List;

public record StyleProfile(
        String displayName,
        List<String> stylePreferences,
        List<String> colorPreferences,
        List<String> occasions,
        List<String> styleTags,
        List<String> tryStyleTags,
        List<String> colorSuggestions,
        List<String> itemSuggestions,
        String reasonSummary,
        String modelName,
        Instant generatedAt,
        boolean stale) {
}
