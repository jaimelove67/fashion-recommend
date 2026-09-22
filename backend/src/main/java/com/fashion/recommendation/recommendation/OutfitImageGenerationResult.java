package com.fashion.recommendation.recommendation;

public record OutfitImageGenerationResult(
        String status,
        String imageUrl,
        String model,
        String requestId,
        String message) {
    public boolean succeeded() {
        return "SUCCEEDED".equals(status) && imageUrl != null && !imageUrl.isBlank();
    }
}
