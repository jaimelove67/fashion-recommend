package com.fashion.recommendation.recommendation;

import java.util.List;

public record RecommendationPage(
        List<Recommendation> content,
        long totalElements,
        int page,
        int size,
        boolean hasNext) {
}
