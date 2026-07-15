package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.style.StyleProfile;
import com.fashion.recommendation.weather.WeatherSnapshot;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import java.util.List;
import java.util.Map;

public record LlmRecommendationContext(
        String occasion,
        String styleHint,
        List<WardrobeItem> wardrobe,
        WeatherSnapshot weather,
        StyleProfile styleProfile,
        Map<Long, Double> itemRatings) {

    public LlmRecommendationContext {
        wardrobe = List.copyOf(wardrobe);
        itemRatings = Map.copyOf(itemRatings);
    }
}
