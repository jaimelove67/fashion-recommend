package com.fashion.recommendation.recommendation;

/**
 * Thrown by the Bailian recommendation client when a request cannot be sent or a response cannot
 * be accepted. Carries a stable {@link RecommendationFallbackReason} code so the service can persist
 * a stable audit reason instead of raw exception text.
 */
public class LlmRecommendationException extends RuntimeException {
    private final String reason;

    public LlmRecommendationException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public LlmRecommendationException(String reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}