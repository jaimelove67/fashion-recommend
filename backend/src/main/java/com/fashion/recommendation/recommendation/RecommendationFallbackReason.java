package com.fashion.recommendation.recommendation;

/**
 * Stable audit codes persisted in {@code recommendations.fallback_reason} whenever an LLM
 * recommendation falls back to the rule engine. Kept as plain string constants so the value is
 * stable across releases and requires no Java enum migration; callers must never persist raw
 * exception text here.
 */
public final class RecommendationFallbackReason {
    public static final String LLM_DISABLED = "llm-disabled";
    public static final String NO_API_KEY = "missing-api-key";
    public static final String REQUEST_FAILED = "request-failed";
    public static final String RESPONSE_INVALID = "response-invalid";
    public static final String RESULT_INVALID = "result-invalid";
    public static final String DUPLICATE_ITEM_IDS = "duplicate-item-ids";
    public static final String FOREIGN_ITEM_IDS = "foreign-item-ids";
    public static final String SAME_CATEGORY = "same-category";

    private RecommendationFallbackReason() {
    }
}