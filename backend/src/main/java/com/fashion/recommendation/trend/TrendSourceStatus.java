package com.fashion.recommendation.trend;

import java.time.Instant;

public record TrendSourceStatus(String id, Instant lastAttemptAt, Instant lastSuccessAt,
        String state, String message, int itemCount, int eligibleCount, String exclusionReason) {

    /**
     * Keeps the persisted-source constructor compatible with callers that only know how many
     * records were collected.  {@code eligibleCount} and {@code exclusionReason} are calculated
     * for the requested time window by {@link TrendService}; they are deliberately not inferred
     * from the collection count here.
     */
    public TrendSourceStatus(String id, Instant lastAttemptAt, Instant lastSuccessAt,
            String state, String message, int itemCount) {
        this(id, lastAttemptAt, lastSuccessAt, state, message, itemCount, 0, null);
    }
}
