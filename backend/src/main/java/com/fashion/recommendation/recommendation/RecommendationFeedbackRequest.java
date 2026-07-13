package com.fashion.recommendation.recommendation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RecommendationFeedbackRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        String feedbackType,
        String comment) {
}
