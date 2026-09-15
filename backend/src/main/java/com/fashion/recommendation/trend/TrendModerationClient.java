package com.fashion.recommendation.trend;

public interface TrendModerationClient {
    boolean enabled();

    TrendAiReviewResult review(TrendItem item);
}
