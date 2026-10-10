package com.fashion.recommendation.tryon;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

public record TryOnFavorite(
        Long id, String name, String category, String imageUrl, String sourceUrl,
        String sourceKind, Instant createdAt, @JsonIgnore String imageObjectKey) {
}
