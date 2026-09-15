package com.fashion.recommendation.trend;

import java.util.Set;

public final class TrendModerationStatus {
    public static final String PENDING_AI = "PENDING_AI";
    public static final String AI_FAILED = "AI_FAILED";
    public static final String PENDING_HUMAN = "PENDING_HUMAN";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final Set<String> VALUES = Set.of(PENDING_AI, AI_FAILED, PENDING_HUMAN, APPROVED, REJECTED);

    private TrendModerationStatus() {
    }
}
