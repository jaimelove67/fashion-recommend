package com.fashion.recommendation.trend;

public record TrendAiReviewResult(
        String decision,
        String riskLevel,
        String reason,
        String modelName,
        String providerCallId,
        String promptVersion) {
}
