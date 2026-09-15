package com.fashion.recommendation.auth;

import java.util.List;
import org.springframework.security.core.Authentication;

public record AuthUserResponse(String username, List<String> authorities) {
    public AuthUserResponse {
        authorities = authorities == null ? List.of() : List.copyOf(authorities);
    }

    public AuthUserResponse(String username) {
        this(username, List.of());
    }

    public static AuthUserResponse from(Authentication authentication) {
        return new AuthUserResponse(
                authentication.getName(),
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .sorted()
                        .toList());
    }
}
