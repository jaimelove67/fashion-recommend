package com.fashion.recommendation.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WeatherServiceTest {
    private static final String WTTR_WEATHER = """
            {"current_condition":[{"temp_C":"26","FeelsLikeC":"28","precipMM":"0.2",\
            "weatherCode":"116","windspeedKmph":"9"}]}
            """;
    private static final String OPEN_METEO_GEOCODING = """
            {"results":[{"latitude":28.2282,"longitude":112.9388,"name":"Changsha"}]}
            """;
    private static final String OPEN_METEO_WEATHER = """
            {"current":{"time":"2026-07-13T10:00","temperature_2m":25.1,\
            "apparent_temperature":26.0,"precipitation":0.0,"weather_code":1,\
            "wind_speed_10m":8.3}}
            """;

    private MockRestServiceServer wttrServer;
    private MockRestServiceServer geocodingServer;
    private MockRestServiceServer forecastServer;
    private SimpleMeterRegistry meterRegistry;
    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();

        RestClient.Builder wttrBuilder = RestClient.builder().baseUrl("https://wttr.test");
        wttrServer = MockRestServiceServer.bindTo(wttrBuilder).build();

        RestClient.Builder geocodingBuilder = RestClient.builder().baseUrl("https://geocoding.test");
        geocodingServer = MockRestServiceServer.bindTo(geocodingBuilder).build();

        RestClient.Builder forecastBuilder = RestClient.builder().baseUrl("https://forecast.test");
        forecastServer = MockRestServiceServer.bindTo(forecastBuilder).build();

        WttrWeatherClient primary = new WttrWeatherClient(wttrBuilder.build(), objectMapper);
        OpenMeteoWeatherClient fallback = new OpenMeteoWeatherClient(
                geocodingBuilder.build(), forecastBuilder.build(), objectMapper);
        Cache<String, WeatherSnapshot> cache = Caffeine.newBuilder()
                .maximumSize(20)
                .recordStats()
                .build();
        meterRegistry = new SimpleMeterRegistry();
        weatherService = new WeatherService(primary, fallback, cache, meterRegistry);
    }

    @AfterEach
    void verifyRequests() {
        wttrServer.verify();
        geocodingServer.verify();
        forecastServer.verify();
        meterRegistry.close();
    }

    @Test
    void returnsPrimaryWeatherAndNormalizesItsWeatherCode() {
        expectWttrSuccess();

        WeatherSnapshot result = weatherService.current("Changsha");

        assertEquals("Changsha", result.city());
        assertEquals(26.0, result.temperatureC());
        assertEquals(28.0, result.apparentTemperatureC());
        assertEquals(0.2, result.precipitationMm());
        assertEquals(2, result.weatherCode());
        assertEquals(9.0, result.windSpeedKmh());
        assertEquals("wttr.in", result.source());
    }

    @Test
    void normalizesCityBeforeUsingTheCacheAndDoesNotRepeatTheRequest() {
        expectWttrSuccess();

        WeatherSnapshot first = weatherService.current("  Changsha   City  ");
        WeatherSnapshot cached = weatherService.current("changsha city");

        assertEquals("Changsha City", first.city());
        assertSame(first, cached);
        assertEquals(1.0, cacheMetric("miss"));
        assertEquals(1.0, cacheMetric("hit"));
    }

    @Test
    void fallsBackToOpenMeteoWhenPrimaryTimesOut() {
        expectWttrTimeout();
        expectOpenMeteoSuccess();

        WeatherSnapshot result = weatherService.current("Changsha");

        assertEquals("open-meteo", result.source());
        assertEquals(25.1, result.temperatureC());
        assertEquals(26.0, result.apparentTemperatureC());
        assertEquals(0.0, result.precipitationMm());
        assertEquals(1, result.weatherCode());
        assertEquals(8.3, result.windSpeedKmh());
    }

    @Test
    void returnsServiceUnavailableWhenBothProvidersFailAndDoesNotCacheTheFailure() {
        expectWttrTimeout();
        geocodingServer.expect(once(), requestTo(startsWith("https://geocoding.test/v1/search")))
                .andRespond(withServiceUnavailable());
        expectWttrSuccess();

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class, () -> weatherService.current("Changsha"));
        WeatherSnapshot retry = weatherService.current("Changsha");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
        assertEquals("天气服务暂时不可用，请稍后重试", failure.getReason());
        assertEquals("wttr.in", retry.source());
    }

    @Test
    void returnsNotFoundOnlyAfterFallbackAlsoCannotResolveTheCity() {
        wttrServer.expect(once(), requestTo(startsWith("https://wttr.test/")))
                .andRespond(withResourceNotFound());
        geocodingServer.expect(once(), requestTo(startsWith("https://geocoding.test/v1/search")))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class, () -> weatherService.current("No Such City"));

        assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
        assertEquals("未找到该城市的实时天气", failure.getReason());
    }

    @Test
    void recordsProviderDurationOutcomeAndFallbackMetrics() {
        expectWttrTimeout();
        expectOpenMeteoSuccess();

        weatherService.current("Changsha");

        assertEquals(1.0, providerRequestMetric("wttr.in", "timeout"));
        assertEquals(1.0, providerRequestMetric("open-meteo", "success"));
        assertEquals(1.0, meterRegistry.get("fashion.weather.fallbacks")
                .tag("outcome", "success").counter().count());
        assertEquals(1L, meterRegistry.get("fashion.weather.provider.duration")
                .tag("provider", "wttr.in").timer().count());
        assertEquals(1L, meterRegistry.get("fashion.weather.provider.duration")
                .tag("provider", "open-meteo").timer().count());
    }

    private void expectWttrSuccess() {
        wttrServer.expect(once(), requestTo(startsWith("https://wttr.test/")))
                .andExpect(queryParam("format", "j1"))
                .andRespond(withSuccess(WTTR_WEATHER, MediaType.APPLICATION_JSON));
    }

    private void expectWttrTimeout() {
        wttrServer.expect(once(), requestTo(startsWith("https://wttr.test/")))
                .andRespond(withException(new SocketTimeoutException("timed out")));
    }

    private void expectOpenMeteoSuccess() {
        geocodingServer.expect(once(), requestTo(startsWith("https://geocoding.test/v1/search")))
                .andExpect(queryParam("count", "1"))
                .andRespond(withSuccess(OPEN_METEO_GEOCODING, MediaType.APPLICATION_JSON));
        forecastServer.expect(once(), requestTo(startsWith("https://forecast.test/v1/forecast")))
                .andExpect(queryParam("timezone", "Asia/Shanghai"))
                .andRespond(withSuccess(OPEN_METEO_WEATHER, MediaType.APPLICATION_JSON));
    }

    private double providerRequestMetric(String provider, String outcome) {
        return meterRegistry.get("fashion.weather.provider.requests")
                .tag("provider", provider)
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private double cacheMetric(String result) {
        return meterRegistry.get("cache.gets")
                .tag("cache", "weather-current")
                .tag("result", result)
                .functionCounter()
                .count();
    }
}
