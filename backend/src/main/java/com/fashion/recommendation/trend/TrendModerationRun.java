package com.fashion.recommendation.trend;

public record TrendModerationRun(
        int requested,
        int reviewed,
        int failed,
        long pendingAi,
        long pendingHuman,
        long aiFailed,
        boolean aiEnabled) {
}
