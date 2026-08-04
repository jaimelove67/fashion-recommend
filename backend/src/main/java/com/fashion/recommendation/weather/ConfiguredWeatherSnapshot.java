package com.fashion.recommendation.weather;

import java.time.Instant;
import org.springframework.util.StringUtils;

/**
 * Static, explicitly configured weather snapshot used only as an offline defense for a
 * presentation/demo when both real weather providers are unreachable. It is never real-time
 * weather: the emitted {@link WeatherSnapshot#source()} is {@code configured-demo} so callers can
 * label it as non-live, and it only ever matches a single configured city.
 *
 * <p>Configuration is validated at construction time (startup): when the demo switch is enabled but
 * the snapshot is incomplete, non-finite, implausible, or negative, the application fails fast
 * rather than silently serving bogus values. When the switch is disabled the snapshot is ignored
 * entirely and the configured values are not validated.
 */
final class ConfiguredWeatherSnapshot {
    private static final double MIN_PLAUSIBLE_TEMP_C = -100.0;
    private static final double MAX_PLAUSIBLE_TEMP_C = 60.0;

    private final boolean enabled;
    private final String city;
    private final double temperatureC;
    private final double apparentTemperatureC;
    private final double precipitationMm;
    private final int weatherCode;
    private final double windSpeedKmh;

    private ConfiguredWeatherSnapshot(
            boolean enabled,
            String city,
            double temperatureC,
            double apparentTemperatureC,
            double precipitationMm,
            int weatherCode,
            double windSpeedKmh) {
        this.enabled = enabled;
        this.city = city;
        this.temperatureC = temperatureC;
        this.apparentTemperatureC = apparentTemperatureC;
        this.precipitationMm = precipitationMm;
        this.weatherCode = weatherCode;
        this.windSpeedKmh = windSpeedKmh;
    }

    static ConfiguredWeatherSnapshot disabled() {
        return new ConfiguredWeatherSnapshot(false, "", 0.0, 0.0, 0.0, 0, 0.0);
    }

    static ConfiguredWeatherSnapshot from(
            boolean enabled,
            String city,
            Double temperatureC,
            Double apparentTemperatureC,
            Double precipitationMm,
            Integer weatherCode,
            Double windSpeedKmh) {
        if (!enabled) {
            return disabled();
        }
        if (!StringUtils.hasText(city)) {
            throw new IllegalArgumentException(
                    "app.weather.configured-city must not be blank when app.weather.configured-demo-enabled=true");
        }
        requireFinite("app.weather.configured-temperature-c", temperatureC);
        requireFinite("app.weather.configured-apparent-temperature-c", apparentTemperatureC);
        requireFinite("app.weather.configured-precipitation-mm", precipitationMm);
        requireFinite("app.weather.configured-wind-speed-kmh", windSpeedKmh);
        if (weatherCode == null) {
            throw new IllegalArgumentException(
                    "app.weather.configured-weather-code must be set when app.weather.configured-demo-enabled=true");
        }
        if (temperatureC < MIN_PLAUSIBLE_TEMP_C || temperatureC > MAX_PLAUSIBLE_TEMP_C) {
            throw new IllegalArgumentException(
                    "app.weather.configured-temperature-c is outside a plausible range: " + temperatureC);
        }
        if (precipitationMm < 0) {
            throw new IllegalArgumentException("app.weather.configured-precipitation-mm must not be negative");
        }
        if (windSpeedKmh < 0) {
            throw new IllegalArgumentException("app.weather.configured-wind-speed-kmh must not be negative");
        }
        return new ConfiguredWeatherSnapshot(
                true, city.trim(), temperatureC, apparentTemperatureC, precipitationMm, weatherCode, windSpeedKmh);
    }

    private static void requireFinite(String property, Double value) {
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    property + " must be a finite number when app.weather.configured-demo-enabled=true");
        }
    }

    boolean enabled() {
        return enabled;
    }

    boolean matches(String normalizedCity) {
        return city.equalsIgnoreCase(normalizedCity);
    }

    WeatherSnapshot toSnapshot() {
        return new WeatherSnapshot(
                city, temperatureC, apparentTemperatureC, precipitationMm, weatherCode, windSpeedKmh,
                Instant.now(), "configured-demo");
    }
}