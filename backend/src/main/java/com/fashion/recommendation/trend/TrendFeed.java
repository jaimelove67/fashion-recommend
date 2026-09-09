package com.fashion.recommendation.trend;

import java.time.Instant;
import java.util.List;

public record TrendFeed(
        String primarySource,
        Instant fetchedAt,
        boolean demoMode,
        List<TrendItem> items,
        String scoreLabel) {

    public TrendFeed(String primarySource, Instant fetchedAt, boolean demoMode, List<TrendItem> items) {
        this(primarySource, fetchedAt, demoMode, items, "授权源热度");
    }
}
