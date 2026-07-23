package com.fashion.recommendation.trend;

public class TrendSourceException extends RuntimeException {
    public TrendSourceException(String message) {
        super(message);
    }

    public TrendSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
