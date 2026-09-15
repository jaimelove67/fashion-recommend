package com.fashion.recommendation.trend;

import java.time.Instant;
import java.util.List;

public record TrendModerationItem(
        String id,
        String platform,
        String title,
        List<String> topicTags,
        int heatScore,
        Instant publishedAt,
        Instant fetchedAt,
        String sourceUrl,
        boolean hidden,
        String imageUrl,
        String summary,
        String moderationStatus,
        String aiDecision,
        String aiRiskLevel,
        String aiReason,
        String aiModel,
        String aiProviderCallId,
        String aiPromptVersion,
        Instant aiReviewedAt,
        String reviewedBy,
        Instant reviewedAt,
        String reviewNote) {

    public TrendItem asTrendItem() {
        return new TrendItem(id, platform, title, topicTags, heatScore, publishedAt, fetchedAt,
                sourceUrl, false, imageUrl, summary);
    }
}
