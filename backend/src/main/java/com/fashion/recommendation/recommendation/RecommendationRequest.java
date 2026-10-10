package com.fashion.recommendation.recommendation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import java.util.List;

public record RecommendationRequest(
        @NotBlank @Size(max = 80) String occasion,
        @NotBlank @Size(max = 80) String city,
        @Size(max = 120) String styleHint,
        @Size(max = 160) String trendId,
        @Size(max = 4) List<@NotNull @Positive Long> lockedItemIds,
        @Size(max = 100) List<@NotNull @Positive Long> excludedItemIds,
        @DecimalMin("-50") @DecimalMax("60") Double manualTemperatureC,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude) {
    @jakarta.validation.constraints.AssertTrue(message = "定位坐标必须成对提供，并用于当前位置")
    public boolean isLocationValid() {
        return latitude == null && longitude == null
                || latitude != null && longitude != null && "当前位置".equals(city);
    }

    public RecommendationRequest {
        lockedItemIds = lockedItemIds == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(lockedItemIds));
        excludedItemIds = excludedItemIds == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(excludedItemIds));
    }
    public RecommendationRequest(String occasion, String city, String styleHint, String trendId) {
        this(occasion, city, styleHint, trendId, List.of(), List.of(), null, null, null);
    }
    public RecommendationRequest(String occasion, String city, String styleHint) {
        this(occasion, city, styleHint, null);
    }
}
