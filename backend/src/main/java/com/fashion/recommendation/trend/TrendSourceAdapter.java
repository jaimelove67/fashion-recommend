package com.fashion.recommendation.trend;

import java.util.List;

public interface TrendSourceAdapter {
    String platform();

    List<TrendItem> fetchPublicSnapshots();

    /**
     * True when a fetch that returned zero relevant items still means the source itself works.
     * A public hot board that was read successfully but holds no fashion word today is a healthy
     * source with an empty result; an unconfigured endpoint is not. Defaults to false so existing
     * adapters keep their current semantics.
     */
    default boolean emptyResultIsHealthy() {
        return false;
    }

    default String scoreLabel() {
        return "授权源热度";
    }
}
