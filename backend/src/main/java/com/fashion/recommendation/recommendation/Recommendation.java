package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.weather.WeatherSnapshot;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import java.time.Instant;
import java.util.List;

public record Recommendation(
        Long id,
        String occasion,
        String city,
        Double temperatureC,
        String summary,
        String reason,
        String engine,
        boolean saved,
        Instant generatedAt,
        WeatherSnapshot weather,
        RecommendationFeedback feedback,
        List<WardrobeItem> items) {
}
