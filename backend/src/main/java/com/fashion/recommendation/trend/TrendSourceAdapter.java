package com.fashion.recommendation.trend;

import java.util.List;

public interface TrendSourceAdapter {
    String platform();

    List<TrendItem> fetchPublicSnapshots();

    default String scoreLabel() {
        return "授权源热度";
    }
}
