package com.fashion.recommendation.admin;

import java.util.List;

public record AdminUser(
        String username,
        boolean enabled,
        List<String> authorities) {
    public AdminUser {
        authorities = authorities == null ? List.of() : List.copyOf(authorities);
    }
}
