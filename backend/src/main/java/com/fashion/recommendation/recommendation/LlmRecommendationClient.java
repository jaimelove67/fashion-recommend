package com.fashion.recommendation.recommendation;

import java.util.Optional;

public interface LlmRecommendationClient {
    Optional<LlmRecommendationResult> recommend(LlmRecommendationContext context);
}
