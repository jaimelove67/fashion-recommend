package com.fashion.recommendation.recommendation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record VisualItemsRequest(@Size(min = 2, max = 4) List<@jakarta.validation.constraints.NotNull @Valid Item> items) {
    public record Item(Long wardrobeId, @Size(max = 120) String name,
            @Size(max = 40) String category, @Size(max = 2800000) String imageData) {}
}
