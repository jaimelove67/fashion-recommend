package com.fashion.recommendation.admin;

public record AdminOverview(
        long totalUsers,
        long enabledUsers,
        long disabledUsers,
        long totalWardrobeItems,
        long totalRecommendations,
        long totalFeedback,
        double averageRating,
        long savedRecommendations,
        long llmRecommendations,
        long fallbackRecommendations,
        long pendingFeedback,
        long manualReviewItems,
        long pendingImageCleanupTasks) {
}
