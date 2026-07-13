package com.fashion.recommendation.weather;

import java.time.Instant;

public record WeatherSnapshot(
        String city,
        double temperatureC,
        double apparentTemperatureC,
        double precipitationMm,
        int weatherCode,
        double windSpeedKmh,
        Instant observedAt,
        String source) {
}
