package com.fashion.recommendation.recommendation;

import java.time.Instant;

public record RecommendationFeedback(
        int rating,
        String feedbackType,
        String comment,
        Instant updatedAt) {
}
