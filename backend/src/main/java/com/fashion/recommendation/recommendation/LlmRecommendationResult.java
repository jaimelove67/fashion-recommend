package com.fashion.recommendation.recommendation;

import java.util.List;

public record LlmRecommendationResult(String summary, String reason, List<Long> itemIds) {
    public LlmRecommendationResult {
        itemIds = itemIds == null ? null : List.copyOf(itemIds);
    }
}
