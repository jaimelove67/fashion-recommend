package com.fashion.recommendation.recommendation;

import jakarta.validation.constraints.NotBlank;

public record RecommendationRequest(
        @NotBlank String occasion,
        @NotBlank String city,
        Double temperatureC,
        String styleHint) {
}
