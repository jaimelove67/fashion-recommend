package com.fashion.recommendation.style;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StyleProfileRefreshRequest(
        @NotBlank @Size(max = 80) String displayName,
        @Pattern(regexp = "MALE|FEMALE", message = "gender must be MALE or FEMALE") String gender,
        @Size(max = 10) List<@Size(max = 40) String> stylePreferences,
        @Size(max = 10) List<@Size(max = 40) String> colorPreferences,
        @Size(max = 10) List<@Size(max = 40) String> occasions) {
}
