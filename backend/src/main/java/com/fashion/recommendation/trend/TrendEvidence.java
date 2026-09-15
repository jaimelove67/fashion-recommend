package com.fashion.recommendation.trend;

import java.util.List;

/** Only observed platform counters are stored. Null means unavailable, never zero. */
public record TrendEvidence(String author, String mediaType, List<String> images,
        Long likes, Long favorites, Long comments, Long reposts, String scoreLabel, Long interactionGrowth) {
    public TrendEvidence {
        images = images == null ? List.of() : List.copyOf(images);
    }
    public boolean hasCounters() {
        return likes != null || favorites != null || comments != null || reposts != null;
    }
    public TrendEvidence scored(String label, Long growth) {
        return new TrendEvidence(author, mediaType, images, likes, favorites, comments, reposts, label, growth);
    }
}
