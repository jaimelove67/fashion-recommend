package com.fashion.recommendation.style;

import jakarta.validation.constraints.Size;
import java.util.List;

/** Visible observations and optional clothing advice; unknown observations stay empty. */
public record PersonalStyleAnalysis(
        @Size(max = 120) String faceShape,
        @Size(max = 120) String facialLine,
        @Size(max = 120) String visualContrast,
        @Size(max = 120) String hairFeatures,
        @Size(max = 240) String bodyProportions,
        @Size(max = 10) List<@Size(max = 80) String> fitSuggestions,
        @Size(max = 10) List<@Size(max = 40) String> styleTags,
        @Size(max = 10) List<@Size(max = 40) String> tryStyleTags,
        @Size(max = 10) List<@Size(max = 40) String> colorSuggestions,
        @Size(max = 10) List<@Size(max = 80) String> itemSuggestions,
        @Size(max = 1200) String reasonSummary) {

    public PersonalStyleAnalysis {
        faceShape = text(faceShape);
        facialLine = text(facialLine);
        visualContrast = text(visualContrast);
        hairFeatures = text(hairFeatures);
        bodyProportions = text(bodyProportions);
        fitSuggestions = values(fitSuggestions);
        styleTags = values(styleTags);
        tryStyleTags = values(tryStyleTags);
        colorSuggestions = values(colorSuggestions);
        itemSuggestions = values(itemSuggestions);
        reasonSummary = text(reasonSummary);
    }

    public boolean hasObservations() {
        return !faceShape.isBlank() || !facialLine.isBlank() || !visualContrast.isBlank()
                || !hairFeatures.isBlank() || !bodyProportions.isBlank();
    }

    public boolean hasContent() {
        return hasObservations() || !fitSuggestions.isEmpty() || !styleTags.isEmpty()
                || !colorSuggestions.isEmpty() || !itemSuggestions.isEmpty();
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }

    private static List<String> values(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).distinct().toList();
    }
}
