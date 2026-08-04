package com.fashion.recommendation.recommendation;

import java.util.List;

/**
 * A validated LLM recommendation plus the audit metadata observed on the provider response.
 * {@code providerCallId}, {@code modelName} and the token fields are only ever populated from a
 * real, successful provider response; rule fallback and the 3-argument convenience constructor
 * leave them null rather than fabricating values.
 */
public record LlmRecommendationResult(
        String summary,
        String reason,
        List<Long> itemIds,
        String providerCallId,
        String modelName,
        String promptVersion,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {

    public LlmRecommendationResult(String summary, String reason, List<Long> itemIds) {
        this(summary, reason, itemIds, null, null, null, null, null, null);
    }

    public LlmRecommendationResult {
        itemIds = itemIds == null ? null : List.copyOf(itemIds);
    }
}