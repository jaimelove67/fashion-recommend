package com.fashion.recommendation.recommendation;

public record RecommendationVisualResponse(
        String status,
        String imageUrl,
        String model,
        String modelGender,
        int itemCount,
        String message) {
}
