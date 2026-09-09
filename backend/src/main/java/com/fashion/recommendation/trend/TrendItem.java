package com.fashion.recommendation.trend;

import java.time.Instant;
import java.util.List;

public record TrendItem(
        String id,
        String platform,
        String title,
        List<String> topicTags,
        int heatScore,
        Instant publishedAt,
        Instant fetchedAt,
        String sourceUrl,
        boolean stale,
        String imageUrl,
        String summary) {

    public TrendItem(
            String id,
            String platform,
            String title,
            List<String> topicTags,
            int heatScore,
            Instant publishedAt,
            Instant fetchedAt,
            String sourceUrl,
            boolean stale,
            String imageUrl) {
        this(id, platform, title, topicTags, heatScore, publishedAt, fetchedAt, sourceUrl, stale, imageUrl, null);
    }
}
