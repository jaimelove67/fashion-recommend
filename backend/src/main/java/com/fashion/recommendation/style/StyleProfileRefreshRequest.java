package com.fashion.recommendation.style;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StyleProfileRefreshRequest(
        @NotBlank @Size(max = 80) String displayName,
        @Size(max = 10) List<@Size(max = 40) String> stylePreferences,
        @Size(max = 10) List<@Size(max = 40) String> colorPreferences,
        @Size(max = 10) List<@Size(max = 40) String> occasions) {
}
