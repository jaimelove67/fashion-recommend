package com.fashion.recommendation.recommendation;

import java.time.Instant;

record RecommendationRecord(
        Long id,
        String occasion,
        String city,
        Double temperatureC,
        String summary,
        String reason,
        String engine,
        boolean saved,
        Instant generatedAt,
        Double apparentTemperatureC,
        Double precipitationMm,
        Integer weatherCode,
        Double windSpeedKmh,
        Instant weatherObservedAt,
        String weatherSource) {
}
