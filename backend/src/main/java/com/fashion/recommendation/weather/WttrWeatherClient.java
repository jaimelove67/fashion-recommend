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
class WttrWeatherClient {
    static final String PROVIDER = "wttr.in";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    WttrWeatherClient(
            ObjectMapper objectMapper,
            @Value("${app.weather.primary-base-url:https://wttr.in}") String baseUrl,
            @Value("${app.weather.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.weather.read-timeout:3s}") Duration readTimeout) {
        this(createRestClient(baseUrl, connectTimeout, readTimeout), objectMapper);
    }

    WttrWeatherClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    WeatherSnapshot current(String city) {
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.pathSegment(city).queryParam("format", "j1").build())
                    .retrieve()
                    .body(String.class);
            JsonNode current = body == null ? null : objectMapper.readTree(body).path("current_condition").path(0);
            if (current == null || current.isMissingNode() || current.isNull()) {
                throw WeatherProviderException.notFound(PROVIDER);
            }
            return new WeatherSnapshot(
                    city,
                    requiredDouble(current, "temp_C"),
                    requiredDouble(current, "FeelsLikeC"),
                    requiredDouble(current, "precipMM"),
                    toWmoCode(requiredInt(current, "weatherCode")),
                    requiredDouble(current, "windspeedKmph"),
                    Instant.now(),
                    PROVIDER);
        } catch (WeatherProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw WeatherProviderException.fromHttp(PROVIDER, exception, true);
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
        if (value == null || (!value.isNumber() && !value.isTextual())) {
            throw new IllegalArgumentException("Missing numeric field: " + fieldName);
        }
        double parsed = value.isNumber() ? value.doubleValue() : Double.parseDouble(value.textValue());
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException("Non-finite numeric field: " + fieldName);
        }
        return parsed;
    }

    private static int requiredInt(JsonNode object, String fieldName) {
        double value = requiredDouble(object, fieldName);
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid integer field: " + fieldName);
        }
        return (int) value;
    }

    // wttr.in uses WorldWeatherOnline codes; normalize them to Open-Meteo's WMO code contract.
    private static int toWmoCode(int wttrCode) {
        return switch (wttrCode) {
            case 113 -> 0;
            case 116 -> 2;
            case 119, 122 -> 3;
            case 143, 248 -> 45;
            case 260 -> 48;
            case 263 -> 51;
            case 266 -> 53;
            case 185, 281 -> 56;
            case 284 -> 57;
            case 176, 353 -> 80;
            case 293, 296 -> 61;
            case 299, 302 -> 63;
            case 305, 308 -> 65;
            case 311, 317 -> 66;
            case 182, 314, 320 -> 67;
            case 179, 323, 326 -> 71;
            case 227, 329, 332 -> 73;
            case 230, 335, 338 -> 75;
            case 350 -> 77;
            case 356 -> 81;
            case 359 -> 82;
            case 362, 368, 374 -> 85;
            case 365, 371, 377 -> 86;
            case 200, 386, 389, 392, 395 -> 95;
            default -> throw new IllegalArgumentException("Unsupported wttr.in weather code: " + wttrCode);
        };
    }
}
