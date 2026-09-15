package com.fashion.recommendation.trend;

import java.time.Instant;

public record TrendSourceStatus(String id, Instant lastAttemptAt, Instant lastSuccessAt,
        String state, String message, int itemCount) {}
