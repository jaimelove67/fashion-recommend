package com.fashion.recommendation.recommendation;

/**
 * Audit metadata surfaced alongside a recommendation in the API. {@code engine} stays a top-level
 * field for backward compatibility; the remaining fields live in this nested record so new audit
 * columns do not pollute the top level of the response. For a successful LLM generation the
 * model/token fields carry the actual provider metadata and {@code fallbackReason} is null; for a
 * rule fallback only {@code fallbackReason} is populated and the model/token fields are null.
 */
public record RecommendationAudit(
        String engine,
        String fallbackReason,
        String modelName,
        String promptVersion,
        String providerCallId,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long generationLatencyMs) {
}