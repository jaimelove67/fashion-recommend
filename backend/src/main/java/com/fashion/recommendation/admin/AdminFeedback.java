package com.fashion.recommendation.admin;

import java.time.Instant;

/** Feedback metadata for moderation; recommendation text and wardrobe items stay private. */
public record AdminFeedback(
        long recommendationId,
        String username,
        int rating,
        String feedbackType,
        String comment,
        String moderationStatus,
        String occasion,
        String city,
        String engine,
        String fallbackReason,
        Instant updatedAt) {
}
