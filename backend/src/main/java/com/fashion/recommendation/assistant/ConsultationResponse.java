package com.fashion.recommendation.assistant;

import com.fashion.recommendation.wardrobe.WardrobeItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ConsultationResponse(String answer, List<WardrobeItem> examples, List<Visual> visuals) {
    public record Visual(
            @NotBlank @Size(max = 80) String title,
            @NotNull @Size(min = 1, max = 4) List<@NotNull @Valid Piece> pieces,
            @NotBlank @Size(max = 400) String explanation) {}

    public record Piece(
            @NotBlank @Size(max = 40) String label,
            @NotBlank @Size(max = 30) String color,
            @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String hex) {}

    public record ModelAnswer(
            @NotBlank @Size(max = 2000) String answer,
            @NotNull @Size(max = 4) List<@NotNull @Positive Long> exampleItemIds,
            @NotNull @Size(max = 3) List<@NotNull @Valid Visual> visuals) {}
}
