package com.fashion.recommendation.recommendation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecommendationFeedbackRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 80) String feedbackType,
        @Size(max = 500) String comment) {
}
