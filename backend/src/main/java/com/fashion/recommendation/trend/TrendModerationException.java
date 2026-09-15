package com.fashion.recommendation.trend;

public class TrendModerationException extends RuntimeException {
    private final String reason;

    public TrendModerationException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public TrendModerationException(String reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
