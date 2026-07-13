package com.fashion.recommendation.weather;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class OpenMeteoWeatherClient {
    static final String PROVIDER = "open-meteo";
    private static final String CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m";

    private final RestClient geocodingClient;
    private final RestClient forecastClient;
    private final ObjectMapper objectMapper;

    @Autowired
    OpenMeteoWeatherClient(
            ObjectMapper objectMapper,
            @Value("${app.weather.fallback-geocoding-base-url:https://geocoding-api.open-meteo.com}")
                    String geocodingBaseUrl,
            @Value("${app.weather.fallback-forecast-base-url:https://api.open-meteo.com}") String forecastBaseUrl,
            @Value("${app.weather.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.weather.read-timeout:3s}") Duration readTimeout) {
        this(
                createRestClient(geocodingBaseUrl, connectTimeout, readTimeout),
                createRestClient(forecastBaseUrl, connectTimeout, readTimeout),
                objectMapper);
    }

    OpenMeteoWeatherClient(RestClient geocodingClient, RestClient forecastClient, ObjectMapper objectMapper) {
        this.geocodingClient = geocodingClient;
        this.forecastClient = forecastClient;
        this.objectMapper = objectMapper;
    }

    WeatherSnapshot current(String city) {
        Coordinates coordinates = geocode(city);
        try {
            String body = forecastClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/forecast")
                            .queryParam("latitude", coordinates.latitude())
                            .queryParam("longitude", coordinates.longitude())
                            .queryParam("current", CURRENT_FIELDS)
                            .queryParam("timezone", "Asia/Shanghai")
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode current = body == null ? null : objectMapper.readTree(body).path("current");
            if (current == null || current.isMissingNode() || current.isNull()) {
                throw new IllegalArgumentException("Open-Meteo response is missing current weather");
            }
            return new WeatherSnapshot(
                    city,
                    requiredDouble(current, "temperature_2m"),
                    requiredDouble(current, "apparent_temperature"),
                    requiredDouble(current, "precipitation"),
                    requiredInt(current, "weather_code"),
                    requiredDouble(current, "wind_speed_10m"),
                    Instant.now(),
                    PROVIDER);
        } catch (WeatherProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw WeatherProviderException.fromHttp(PROVIDER, exception, false);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw WeatherProviderException.invalidResponse(PROVIDER, exception);
        }
    }

    private Coordinates geocode(String city) {
        try {
            String body = geocodingClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/search")
                            .queryParam("name", city)
                            .queryParam("count", 1)
                            .queryParam("language", "zh")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode first = body == null ? null : objectMapper.readTree(body).path("results").path(0);
            if (first == null || first.isMissingNode() || first.isNull()) {
                throw WeatherProviderException.notFound(PROVIDER);
            }
            return new Coordinates(requiredDouble(first, "latitude"), requiredDouble(first, "longitude"));
        } catch (WeatherProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw WeatherProviderException.fromHttp(PROVIDER, exception, false);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw WeatherProviderException.invalidResponse(PROVIDER, exception);
        }
    }

    private static RestClient createRestClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", "fashion-recommendation/0.1")
                .build();
    }

    private static double requiredDouble(JsonNode object, String fieldName) {
        JsonNode value = object.get(fieldName);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException("Missing numeric field: " + fieldName);
        }
        double parsed = value.doubleValue();
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException("Non-finite numeric field: " + fieldName);
        }
        return parsed;
    }

    private static int requiredInt(JsonNode object, String fieldName) {
        JsonNode value = object.get(fieldName);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Missing integer field: " + fieldName);
        }
        return value.intValue();
    }

    private record Coordinates(double latitude, double longitude) {
    }
}
