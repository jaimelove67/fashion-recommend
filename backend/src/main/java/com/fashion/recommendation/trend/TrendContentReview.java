package com.fashion.recommendation.trend;

/** An administrator must inspect the cover and every image before approving this fingerprint. */
public record TrendContentReview(TrendItem item, String fingerprint, String status, boolean hidden) {}
