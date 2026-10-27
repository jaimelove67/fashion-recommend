package com.fashion.recommendation.technique;

import java.time.Instant;
import java.util.List;

/** Transient evidence read from the publisher; the full text is never a public API response. */
public record TechniqueArticle(
        String id, String title, String url, String author, String platform,
        String publishedAt, Instant fetchedAt, String text, List<ComparisonImage> comparisonImages) {
    public TechniqueArticle {
        comparisonImages = comparisonImages == null ? List.of() : List.copyOf(comparisonImages);
    }

    public TechniqueArticle(String id, String title, String url, String author, String platform,
            String publishedAt, Instant fetchedAt, String text) {
        this(id, title, url, author, platform, publishedAt, fetchedAt, text, List.of());
    }

    /** Original publisher metadata, not a claim that the image has been visually inspected. */
    public record ComparisonImage(String url, String alt, String caption, String context) {}
}
