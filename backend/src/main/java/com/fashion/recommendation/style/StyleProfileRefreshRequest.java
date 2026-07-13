package com.fashion.recommendation.style;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record StyleProfileRefreshRequest(
        @NotBlank String displayName,
        List<String> stylePreferences,
        List<String> colorPreferences,
        List<String> occasions) {
}
