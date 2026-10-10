package com.fashion.recommendation.style;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.List;

public record StyleProfile(
        String displayName,
        String gender,
        List<String> stylePreferences,
        List<String> colorPreferences,
        List<String> occasions,
        List<String> styleTags,
        List<String> tryStyleTags,
        List<String> colorSuggestions,
        List<String> itemSuggestions,
        String reasonSummary,
        String modelName,
        Instant generatedAt,
        boolean stale,
        Double heightCm,
        Double weightKg,
        String photoUrl,
        @JsonIgnore String photoObjectKey,
        PersonalStyleAnalysis analysis,
        String analysisSource,
        String analysisModelName,
        Instant analysisUpdatedAt,
        @JsonIgnore long revision,
        boolean usePersonalPhotoForOutfit,
        List<String> avoidPreferences,
        boolean preferencesConfirmed) {

    public StyleProfile(String displayName, String gender, List<String> stylePreferences,
            List<String> colorPreferences, List<String> occasions, List<String> styleTags,
            List<String> tryStyleTags, List<String> colorSuggestions, List<String> itemSuggestions,
            String reasonSummary, String modelName, Instant generatedAt, boolean stale,
            Double heightCm, Double weightKg, String photoUrl, String photoObjectKey,
            PersonalStyleAnalysis analysis, String analysisSource, String analysisModelName,
            Instant analysisUpdatedAt, long revision, boolean usePersonalPhotoForOutfit) {
        this(displayName, gender, stylePreferences, colorPreferences, occasions, styleTags,
                tryStyleTags, colorSuggestions, itemSuggestions, reasonSummary, modelName, generatedAt,
                stale, heightCm, weightKg, photoUrl, photoObjectKey, analysis, analysisSource,
                analysisModelName, analysisUpdatedAt, revision, usePersonalPhotoForOutfit, List.of(), false);
    }

    public StyleProfile withPreferenceDetails(List<String> avoid, boolean confirmed) {
        return new StyleProfile(displayName, gender, stylePreferences, colorPreferences, occasions, styleTags,
                tryStyleTags, colorSuggestions, itemSuggestions, reasonSummary, modelName, generatedAt,
                stale, heightCm, weightKg, photoUrl, photoObjectKey, analysis, analysisSource,
                analysisModelName, analysisUpdatedAt, revision, usePersonalPhotoForOutfit, avoid, confirmed);
    }

    public StyleProfile(String displayName, String gender, List<String> stylePreferences,
            List<String> colorPreferences, List<String> occasions, List<String> styleTags,
            List<String> tryStyleTags, List<String> colorSuggestions, List<String> itemSuggestions,
            String reasonSummary, String modelName, Instant generatedAt, boolean stale,
            Double heightCm, Double weightKg, String photoUrl, String photoObjectKey,
            PersonalStyleAnalysis analysis, String analysisSource, String analysisModelName,
            Instant analysisUpdatedAt, long revision) {
        this(displayName, gender, stylePreferences, colorPreferences, occasions, styleTags,
                tryStyleTags, colorSuggestions, itemSuggestions, reasonSummary, modelName, generatedAt,
                stale, heightCm, weightKg, photoUrl, photoObjectKey, analysis, analysisSource,
                analysisModelName, analysisUpdatedAt, revision, false);
    }

    public StyleProfile(String displayName, String gender, List<String> stylePreferences,
            List<String> colorPreferences, List<String> occasions, List<String> styleTags,
            List<String> tryStyleTags, List<String> colorSuggestions, List<String> itemSuggestions,
            String reasonSummary, String modelName, Instant generatedAt, boolean stale) {
        this(displayName, gender, stylePreferences, colorPreferences, occasions, styleTags,
                tryStyleTags, colorSuggestions, itemSuggestions, reasonSummary, modelName, generatedAt,
                stale, null, null, null, null, null, null, null, null, 0);
    }
}
