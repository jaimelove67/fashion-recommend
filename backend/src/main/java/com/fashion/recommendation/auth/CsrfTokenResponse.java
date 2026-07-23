package com.fashion.recommendation.auth;

public record CsrfTokenResponse(String token, String headerName) {
}
