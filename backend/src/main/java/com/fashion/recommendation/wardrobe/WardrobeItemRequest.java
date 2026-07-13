package com.fashion.recommendation.wardrobe;

import jakarta.validation.constraints.NotBlank;

public record WardrobeItemRequest(
        @NotBlank String name,
        @NotBlank String category,
        @NotBlank String color,
        String style,
        String imageUrl) {
}
