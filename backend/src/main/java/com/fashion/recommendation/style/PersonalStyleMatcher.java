package com.fashion.recommendation.style;

import com.fashion.recommendation.wardrobe.WardrobeItem;
import java.util.List;

/** Matches advice against known wardrobe metadata; it cannot infer an item's actual size or fit. */
public final class PersonalStyleMatcher {
    private PersonalStyleMatcher() { }

    public static double score(WardrobeItem item, StyleProfile profile) {
        String description = text(item.name()) + " " + text(item.style()) + " " + text(item.category());
        double preferenceWeight = profile.preferencesConfirmed() ? 2.0 : 1.0;
        double score = preferenceWeight * (matches(description, profile.stylePreferences())
                + 1.5 * matches(text(item.color()), profile.colorPreferences()));
        if (!profile.stale()) {
            score += 0.5 * matches(description, profile.styleTags().stream()
                    .filter(tag -> !profile.stylePreferences().contains(tag)).toList());
            score += 0.75 * matches(text(item.color()), profile.colorSuggestions().stream()
                    .filter(color -> !profile.colorPreferences().contains(color)).toList());
        }
        if (!profile.stale() && profile.analysis() != null) {
            score += 1.5 * matches(description, profile.analysis().fitSuggestions());
            score += matches(description, profile.analysis().itemSuggestions());
        }
        if (profile.preferencesConfirmed()) score -= 4.0 * matches(description + " " + text(item.color()), profile.avoidPreferences());
        return score;
    }

    private static long matches(String value, List<String> suggestions) {
        String normalized = value.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
        return suggestions.stream().filter(suggestion -> !suggestion.isBlank()).map(String::trim).distinct()
                .filter(suggestion -> normalized.contains(suggestion.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT))).count();
    }

    private static String text(String value) { return value == null ? "" : value; }
}
