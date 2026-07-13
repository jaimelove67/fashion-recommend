package com.fashion.recommendation.wardrobe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

public record WardrobeItem(
        Long id,
        String name,
        String category,
        String color,
        String style,
        String imageUrl,
        Instant createdAt,
        String recognitionStatus,
        String recognitionMessage,
        @JsonIgnore String imageObjectKey) {
    public WardrobeItem(
            Long id,
            String name,
            String category,
            String color,
            String style,
            String imageUrl,
            Instant createdAt) {
        this(id, name, category, color, style, imageUrl, createdAt, "MANUAL", null, null);
    }
}
