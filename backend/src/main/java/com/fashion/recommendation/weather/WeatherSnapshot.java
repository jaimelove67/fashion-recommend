package com.fashion.recommendation.weather;

import java.time.Instant;

public record WeatherSnapshot(
        String city,
        double temperatureC,
        Double apparentTemperatureC,
        Double precipitationMm,
        Integer weatherCode,
        Double windSpeedKmh,
        Instant observedAt,
        String source) {
}
