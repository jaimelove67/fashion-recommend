package com.fashion.recommendation.style;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StyleProfileRefreshRequest(
        @NotBlank @Size(max = 80) String displayName,
        @Pattern(regexp = "MALE|FEMALE", message = "gender must be MALE or FEMALE") String gender,
        @Size(max = 10) List<@Size(max = 40) String> stylePreferences,
        @Size(max = 10) List<@Size(max = 40) String> colorPreferences,
        @Size(max = 10) List<@Size(max = 40) String> occasions,
        @DecimalMin("80") @DecimalMax("250") Double heightCm,
        @DecimalMin("20") @DecimalMax("300") Double weightKg,
        @Size(max = 10) List<@Size(max = 40) String> avoidPreferences,
        Boolean confirmPreferences) {

    public StyleProfileRefreshRequest(String displayName, String gender, List<String> stylePreferences,
            List<String> colorPreferences, List<String> occasions, Double heightCm, Double weightKg) {
        this(displayName, gender, stylePreferences, colorPreferences, occasions, heightCm, weightKg, null, null);
    }
}
