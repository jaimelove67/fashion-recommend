package com.fashion.recommendation.technique;

import java.time.Instant;

/** Transient evidence read from the publisher; the full text is never a public API response. */
public record TechniqueArticle(
        String id, String title, String url, String author, String platform,
        String publishedAt, Instant fetchedAt, String text) {
}
