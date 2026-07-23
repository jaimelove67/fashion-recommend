package com.fashion.recommendation.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthRegistrationRequest(
        @NotBlank
        @Pattern(regexp = "[a-z0-9][a-z0-9_-]{2,31}")
        String username,
        @NotBlank
        @Size(min = 8, max = 72)
        String password) {
}
