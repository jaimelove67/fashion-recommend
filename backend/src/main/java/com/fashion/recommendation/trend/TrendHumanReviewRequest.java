package com.fashion.recommendation.trend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrendHumanReviewRequest(
        @NotBlank @Size(max = 16) String status,
        @Size(max = 500) String note) {
}
