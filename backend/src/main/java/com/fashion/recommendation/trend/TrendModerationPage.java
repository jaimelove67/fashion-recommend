package com.fashion.recommendation.trend;

import java.util.List;

public record TrendModerationPage(
        List<TrendModerationItem> items,
        long totalElements,
        int page,
        int size,
        boolean hasNext) {
}
