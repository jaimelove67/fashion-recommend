package com.fashion.recommendation.trend;

import java.time.Instant;
import java.util.List;

public record TrendFeed(String primarySource, Instant fetchedAt, boolean demoMode, List<TrendItem> items) {
}
