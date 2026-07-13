package com.fashion.recommendation.weather;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

final class WeatherProviderException extends RuntimeException {
    enum Outcome {
        TIMEOUT("timeout"),
        RATE_LIMITED("rate_limited"),
        NOT_FOUND("not_found"),
        ERROR("error");

        private final String metricValue;

        Outcome(String metricValue) {
            this.metricValue = metricValue;
        }

        String metricValue() {
            return metricValue;
        }
    }

    private final Outcome outcome;

    private WeatherProviderException(String message, Outcome outcome, Throwable cause) {
        super(message, cause);
        this.outcome = outcome;
    }

    static WeatherProviderException notFound(String provider) {
        return new WeatherProviderException(provider + " could not resolve the city", Outcome.NOT_FOUND, null);
    }

    static WeatherProviderException invalidResponse(String provider, Throwable cause) {
        return new WeatherProviderException(provider + " returned an invalid response", Outcome.ERROR, cause);
    }

    static WeatherProviderException fromHttp(
            String provider, RestClientException exception, boolean httpNotFoundMeansCityNotFound) {
        if (isTimeout(exception)) {
            return new WeatherProviderException(provider + " timed out", Outcome.TIMEOUT, exception);
        }
        if (exception instanceof RestClientResponseException responseException) {
            if (responseException.getStatusCode().value() == 429) {
                return new WeatherProviderException(provider + " rate limited the request", Outcome.RATE_LIMITED, exception);
            }
            if (httpNotFoundMeansCityNotFound
                    && responseException.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return new WeatherProviderException(provider + " could not resolve the city", Outcome.NOT_FOUND, exception);
            }
        }
        return new WeatherProviderException(provider + " request failed", Outcome.ERROR, exception);
    }

    static WeatherProviderException unexpected(String provider, RuntimeException exception) {
        return new WeatherProviderException(provider + " failed unexpectedly", Outcome.ERROR, exception);
    }

    Outcome outcome() {
        return outcome;
    }

    private static boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
