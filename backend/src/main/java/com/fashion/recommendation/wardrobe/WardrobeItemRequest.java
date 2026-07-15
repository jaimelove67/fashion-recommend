package com.fashion.recommendation.wardrobe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WardrobeItemRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 40) String category,
        @NotBlank @Size(max = 40) String color,
        @Size(max = 120) String style,
        @Size(max = 500) String imageUrl) {
}
