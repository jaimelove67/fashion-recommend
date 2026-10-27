package com.fashion.recommendation.technique;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class TechniqueComparisonVisualService {
    private final OutfitTechniqueRepository techniques;
    private final CuratedTechniqueCatalog curated;
    private final TechniqueComparisonVisualRepository repository;
    private final TechniqueSourceCollector collector;
    private final TechniqueComparisonImageClient generator;
    private final Executor executor;
    private final Clock clock;
    private final AtomicBoolean queued = new AtomicBoolean();

    @Autowired
    public TechniqueComparisonVisualService(OutfitTechniqueRepository techniques, CuratedTechniqueCatalog curated,
            TechniqueComparisonVisualRepository repository, TechniqueSourceCollector collector,
            TechniqueComparisonImageClient generator, @Qualifier("techniqueComparisonExecutor") Executor executor) {
        this(techniques, curated, repository, collector, generator, executor, Clock.systemUTC());
    }

    TechniqueComparisonVisualService(OutfitTechniqueRepository techniques, CuratedTechniqueCatalog curated,
            TechniqueComparisonVisualRepository repository, TechniqueSourceCollector collector,
            TechniqueComparisonImageClient generator, Executor executor, Clock clock) {
        this.techniques = techniques;
        this.curated = curated;
        this.repository = repository;
        this.collector = collector;
        this.generator = generator;
        this.executor = executor;
        this.clock = clock;
    }

    @Bean(name = "techniqueComparisonExecutor")
    static ThreadPoolTaskExecutor executor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("technique-comparison-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    public boolean start() {
        if (!queued.compareAndSet(false, true)) return false;
        try {
            executor.execute(() -> { try { backfill(); } finally { queued.set(false); } });
            return true;
        } catch (RuntimeException exception) { queued.set(false); return false; }
    }

    public OutfitTechnique decorate(OutfitTechnique item) { return repository.decorate(item); }
    public TechniqueComparisonVisualRepository.Budget budget() { return repository.budget(clock.instant()); }

    /** Called only by explicit administrator POST or successful scheduled publication, never by a GET. */
    void backfill() {
        var items = java.util.stream.Stream.concat(techniques.latest().stream(), curated.items().stream()).toList();
        for (OutfitTechnique item : items) {
            if (!hasComparison(item) || repository.decorate(item).comparisonVisual() != null) continue;
            List<TechniqueArticle> articles;
            try { articles = "curated".equals(item.generationMode())
                    ? collector.collectSources(item.sources() == null ? List.of() : item.sources()) : List.of(); }
            catch (RuntimeException exception) { articles = List.of(); }
            var source = original(item, articles);
            if (source != null) { repository.source(item, source); continue; }
            if (!generator.available()) continue;
            var reservation = new AtomicReference<String>();
            com.fashion.recommendation.storage.StoredImageData image = null;
            try {
                image = generator.generate(item, () -> {
                    reservation.set(repository.reserve(item, clock.instant()).orElse(null));
                    return reservation.get() != null;
                });
            }
            catch (RuntimeException exception) { /* Failed calls consume their durable reservation. */ }
            if (reservation.get() != null) repository.finish(reservation.get(), item, image, clock.instant());
        }
    }

    static boolean hasComparison(OutfitTechnique item) {
        return item.example() != null && item.example().before() != null && !item.example().before().isBlank()
                && item.example().after() != null && !item.example().after().isBlank();
    }

    static OutfitTechnique.ComparisonVisual original(OutfitTechnique item, List<TechniqueArticle> articles) {
        return original(item, articles, null);
    }

    static OutfitTechnique.ComparisonVisual original(OutfitTechnique item, List<TechniqueArticle> articles, String selectedSourceId) {
        if (!hasComparison(item) || item.sources() == null || articles == null) return null;
        for (TechniqueArticle article : articles) {
            if (article.comparisonImages() == null || article.comparisonImages().isEmpty()
                    || (selectedSourceId != null && !selectedSourceId.equals(article.id()))
                    || item.sources().stream().noneMatch(source -> source.id().equals(article.id()) || source.url().equals(article.url()))) continue;
            if (selectedSourceId == null && !sameVariable(item, article.comparisonImages())) continue;
            var images = article.comparisonImages().stream().filter(image -> safeImageUrl(image.url()))
                    .limit(2).map(image -> new OutfitTechnique.ComparisonVisual.Image(image.url(),
                            image.alt() == null || image.alt().isBlank() ? "原文前后对照图" : image.alt())).toList();
            if (!images.isEmpty()) return new OutfitTechnique.ComparisonVisual("source", images, article.url(),
                    "原文明确标注且与本课调整变量对应的对照图，保留原作者配图与来源。");
        }
        return null;
    }

    private static boolean sameVariable(OutfitTechnique item, List<TechniqueArticle.ComparisonImage> images) {
        String lesson = (item.title() + " " + item.example().before() + " " + item.example().after()).toLowerCase(java.util.Locale.ROOT);
        String captions = images.stream().map(image -> image.alt() + " " + image.caption())
                .collect(java.util.stream.Collectors.joining(" ")).toLowerCase(java.util.Locale.ROOT);
        // Generic before/after labels or a long article's unrelated prose are insufficient.
        for (String expression : List.of("\\b(?:waist(?:line)?|tuck(?:ed|ing)?|high-rise)\\b|腰线|塞衣|高腰",
                "\\b(?:hems?|hemline|trouser length|pant length)\\b|裤脚|裤长", "\\b(?:neckline|collar)\\b|领口",
                "\\b(?:inner[- ]layer room|layering ease)\\b|内层松量|中间层空间", "\\bformality\\b|正式度")) {
            if (java.util.regex.Pattern.compile(expression).matcher(lesson).find()
                    && java.util.regex.Pattern.compile(expression).matcher(captions).find()) return true;
        }
        return lesson.matches("(?s).*(配色|颜色|色彩|\\bcolou?r\\b).*") && lesson.matches("(?s).*(近脸|肤色|\\bface\\b|\\bskin\\b).*")
                && captions.matches("(?s).*(\\bcolou?r\\b|配色|颜色|色彩).*") && captions.matches("(?s).*(\\bface\\b|\\bskin\\b|近脸|肤色).*");
    }

    private static boolean safeImageUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (RuntimeException exception) { return false; }
    }
}
