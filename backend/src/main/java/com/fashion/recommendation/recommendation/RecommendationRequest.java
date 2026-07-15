package com.fashion.recommendation.recommendation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecommendationRequest(
        @NotBlank @Size(max = 80) String occasion,
        @NotBlank @Size(max = 80) String city,
        @Size(max = 120) String styleHint) {
}
