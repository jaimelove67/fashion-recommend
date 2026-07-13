package com.fashion.recommendation.weather;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WeatherService {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final String PROVIDER_REQUESTS = "fashion.weather.provider.requests";
    private static final String PROVIDER_DURATION = "fashion.weather.provider.duration";
    private static final String FALLBACKS = "fashion.weather.fallbacks";

    private final WttrWeatherClient primaryClient;
    private final OpenMeteoWeatherClient fallbackClient;
    private final Cache<String, WeatherSnapshot> cache;
    private final MeterRegistry meterRegistry;

    @Autowired
    public WeatherService(
            WttrWeatherClient primaryClient,
            OpenMeteoWeatherClient fallbackClient,
            MeterRegistry meterRegistry,
            @Value("${app.weather.cache-ttl:15m}") Duration cacheTtl,
            @Value("${app.weather.cache-max-size:500}") long cacheMaxSize) {
        this(primaryClient, fallbackClient, buildCache(cacheTtl, cacheMaxSize), meterRegistry);
    }

    WeatherService(
            WttrWeatherClient primaryClient,
            OpenMeteoWeatherClient fallbackClient,
            Cache<String, WeatherSnapshot> cache,
            MeterRegistry meterRegistry) {
        this.primaryClient = primaryClient;
        this.fallbackClient = fallbackClient;
        this.cache = CaffeineCacheMetrics.monitor(meterRegistry, cache, "weather-current");
        this.meterRegistry = meterRegistry;
    }

    public WeatherSnapshot current(String city) {
        if (!StringUtils.hasText(city)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "城市不能为空");
        }
        String normalizedCity = city.trim().replaceAll("\\s+", " ");
        String cacheKey = normalizedCity.toLowerCase(Locale.ROOT);
        return cache.get(cacheKey, ignored -> loadFresh(normalizedCity));
    }

    private WeatherSnapshot loadFresh(String city) {
        try {
            return observeProvider(WttrWeatherClient.PROVIDER, () -> primaryClient.current(city));
        } catch (WeatherProviderException primaryFailure) {
            log.warn("Primary weather provider failed; using fallback, provider={}, outcome={}",
                    WttrWeatherClient.PROVIDER, primaryFailure.outcome().metricValue());
        }

        try {
            WeatherSnapshot result = observeProvider(OpenMeteoWeatherClient.PROVIDER, () -> fallbackClient.current(city));
            meterRegistry.counter(FALLBACKS, "outcome", "success").increment();
            return result;
        } catch (WeatherProviderException fallbackFailure) {
            meterRegistry.counter(FALLBACKS, "outcome", "failure").increment();
            log.warn("Fallback weather provider failed, provider={}, outcome={}",
                    OpenMeteoWeatherClient.PROVIDER, fallbackFailure.outcome().metricValue());
            if (fallbackFailure.outcome() == WeatherProviderException.Outcome.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该城市的实时天气");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "天气服务暂时不可用，请稍后重试");
        }
    }

    private WeatherSnapshot observeProvider(String provider, Supplier<WeatherSnapshot> request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            WeatherSnapshot result = request.get();
            meterRegistry.counter(PROVIDER_REQUESTS, "provider", provider, "outcome", "success").increment();
            return result;
        } catch (WeatherProviderException exception) {
            meterRegistry.counter(PROVIDER_REQUESTS,
                    "provider", provider, "outcome", exception.outcome().metricValue()).increment();
            throw exception;
        } catch (RuntimeException exception) {
            meterRegistry.counter(PROVIDER_REQUESTS, "provider", provider, "outcome", "error").increment();
            throw WeatherProviderException.unexpected(provider, exception);
        } finally {
            sample.stop(Timer.builder(PROVIDER_DURATION).tag("provider", provider).register(meterRegistry));
        }
    }

    private static Cache<String, WeatherSnapshot> buildCache(Duration cacheTtl, long cacheMaxSize) {
        if (cacheTtl.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("app.weather.cache-ttl must be positive");
        }
        if (cacheMaxSize <= 0) {
            throw new IllegalArgumentException("app.weather.cache-max-size must be positive");
        }
        return Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(cacheTtl)
                .recordStats()
                .build();
    }
}
