package com.fashion.recommendation.trend;

import java.util.List;

/** Only observed platform counters are stored. Null means unavailable, never zero. */
public record TrendEvidence(String author, String mediaType, List<String> images,
        Long likes, Long favorites, Long comments, Long reposts, String scoreLabel, Long interactionGrowth,
        Long authorFollowers, String authorFollowersLabel, String creatorTier, String fullBodyImageUrl) {
    public TrendEvidence {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public TrendEvidence(String author, String mediaType, List<String> images,
            Long likes, Long favorites, Long comments, Long reposts, String scoreLabel, Long interactionGrowth) {
        this(author, mediaType, images, likes, favorites, comments, reposts, scoreLabel, interactionGrowth, null, null, null);
    }

    /** Keeps the pre-label constructor usable for existing adapters and tests. */
    public TrendEvidence(String author, String mediaType, List<String> images,
            Long likes, Long favorites, Long comments, Long reposts, String scoreLabel, Long interactionGrowth,
            Long authorFollowers, String creatorTier) {
        this(author, mediaType, images, likes, favorites, comments, reposts, scoreLabel, interactionGrowth,
                authorFollowers, null, creatorTier, null);
    }

    public TrendEvidence(String author, String mediaType, List<String> images,
            Long likes, Long favorites, Long comments, Long reposts, String scoreLabel, Long interactionGrowth,
            Long authorFollowers, String authorFollowersLabel, String creatorTier) {
        this(author, mediaType, images, likes, favorites, comments, reposts, scoreLabel, interactionGrowth,
                authorFollowers, authorFollowersLabel, creatorTier, null);
    }

    public boolean hasCounters() {
        return likes != null || favorites != null || comments != null || reposts != null;
    }
    public TrendEvidence scored(String label, Long growth) {
        return new TrendEvidence(author, mediaType, images, likes, favorites, comments, reposts, label, growth,
                authorFollowers, authorFollowersLabel, creatorTier, fullBodyImageUrl);
    }

    public TrendEvidence withCreatorTier(String tier) {
        return new TrendEvidence(author, mediaType, images, likes, favorites, comments, reposts, scoreLabel,
                interactionGrowth, authorFollowers, authorFollowersLabel, tier, fullBodyImageUrl);
    }
}
