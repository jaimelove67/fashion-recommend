package com.fashion.recommendation.admin;

import java.time.Instant;

public record AdminAuditLog(
        long id,
        String actorUsername,
        String action,
        String targetType,
        String targetId,
        String outcome,
        String details,
        Instant createdAt) {
}
