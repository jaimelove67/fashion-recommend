package com.fashion.recommendation.technique;

import java.time.Instant;
import java.util.List;

/** Public, attributed synthesis. Full article text and private review evidence are never returned. */
public record OutfitTechnique(
        String id, String category, String title, String summary,
        List<Source> sources, List<SourceNote> sourceNotes, String synthesisNote,
        List<String> steps, String reason, Example example, String suitable,
        String limit, String check, Instant publishedAt, String generationMode, String modelName,
        ComparisonVisual comparisonVisual) {
    public OutfitTechnique(String id, String category, String title, String summary, List<Source> sources,
            List<SourceNote> sourceNotes, String synthesisNote, List<String> steps, String reason, Example example,
            String suitable, String limit, String check, Instant publishedAt, String generationMode, String modelName) {
        this(id, category, title, summary, sources, sourceNotes, synthesisNote, steps, reason, example,
                suitable, limit, check, publishedAt, generationMode, modelName, null);
    }

    public OutfitTechnique withComparisonVisual(ComparisonVisual visual) {
        return new OutfitTechnique(id, category, title, summary, sources, sourceNotes, synthesisNote, steps,
                reason, example, suitable, limit, check, publishedAt, generationMode, modelName, visual);
    }

    public record ComparisonVisual(String kind, List<Image> images, String sourceUrl, String note) {
        public record Image(String url, String alt) {}
    }
    public record Source(String id, String title, String url, String author, String platform,
            String publishedAt, String readAt, String scope) {}
    public record SourceNote(String sourceId, String point) {}
    public record Example(String before, String after) {}
}
