package com.fashion.recommendation.technique;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class WeeklyTechniqueService {
    private final OutfitTechniqueRepository repository;
    private final TechniqueSourceCollector collector;
    private final TechniqueGenerationClient generator;
    private final boolean enabled;
    private final Clock clock;

    @Autowired
    public WeeklyTechniqueService(OutfitTechniqueRepository repository, TechniqueSourceCollector collector,
            TechniqueGenerationClient generator, @Value("${app.techniques.enabled:true}") boolean enabled) {
        this(repository, collector, generator, enabled, Clock.systemUTC());
    }

    WeeklyTechniqueService(OutfitTechniqueRepository repository, TechniqueSourceCollector collector,
            TechniqueGenerationClient generator, boolean enabled, Clock clock) {
        this.repository = repository;
        this.collector = collector;
        this.generator = generator;
        this.enabled = enabled;
        this.clock = clock;
    }

    // A slow source or model call must not hold up wardrobe cleanup or daily trends.
    @Bean(name = "taskScheduler")
    static ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }

    @Bean(name = "techniqueTaskScheduler")
    static ThreadPoolTaskScheduler techniqueTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("weekly-technique-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Scheduled(initialDelayString = "${app.techniques.initial-delay:60000}", scheduler = "techniqueTaskScheduler")
    public void catchUpOnStartup() { refresh(); }

    @Scheduled(cron = "${app.techniques.refresh-cron:0 0 10 * * *}", zone = "Asia/Shanghai", scheduler = "techniqueTaskScheduler")
    public void scheduledRefresh() { refresh(); }

    public TechniqueFeed currentFeed() {
        List<OutfitTechnique> items = repository.latest();
        OperationalStatus status = status();
        return new TechniqueFeed(items, items.isEmpty() ? null : items.get(0).publishedAt(), "weekly", status.status());
    }

    public OperationalStatus status() {
        boolean available = enabled && generator.available();
        var state = repository.state(clock.instant());
        String status = !available ? "disabled" : state.collecting() ? "collecting"
                : state.lastErrorCode() != null || state.lastSuccessAt() == null ? "waiting-for-quality" : "ready";
        return new OperationalStatus(status, available, state.lastAttemptAt(), state.lastSuccessAt(), state.nextDueAt(),
                state.lastErrorCode(), state.attemptsInWindow(), OutfitTechniqueRepository.WEEKLY_ATTEMPT_LIMIT);
    }

    /** Manual refresh deliberately uses the same durable quota and lease as scheduled work. */
    public RefreshResult refresh() {
        if (!enabled || !generator.available()) return new RefreshResult(false, "disabled", status());
        var lease = repository.tryAcquire(clock.instant());
        if (lease.isEmpty()) return new RefreshResult(false, "not-due", status());
        String token = lease.get();
        String errorCode = "source-unavailable";
        try {
            List<TechniqueArticle> articles = collector.collect();
            if (articles == null || articles.stream().map(TechniqueArticle::url).distinct().count() < 2) {
                repository.fail(token, "insufficient-sources", clock.instant());
                return new RefreshResult(true, "waiting-for-quality", status());
            }
            errorCode = "generation-unavailable";
            TechniqueGenerationResult result;
            try { result = generator.generate(articles, repository.recentTitles()); }
            catch (IllegalArgumentException exception) {
                repository.fail(token, "quality-rejected", clock.instant());
                return new RefreshResult(true, "waiting-for-quality", status());
            }
            errorCode = "internal-failure";
            boolean published = repository.publish(token, result, clock.instant());
            if (!published) repository.fail(token, "internal-failure", clock.instant());
            return new RefreshResult(true, published ? "published" : "waiting-for-quality", status());
        } catch (RuntimeException exception) {
            // Persist only a bounded classification; provider messages can contain keys or article text.
            repository.fail(token, errorCode, clock.instant());
            return new RefreshResult(true, "waiting-for-quality", status());
        }
    }

    public record TechniqueFeed(List<OutfitTechnique> items, Instant latestPublishedAt, String cadence, String status) {}
    public record OperationalStatus(String status, boolean enabled, Instant lastAttemptAt, Instant lastSuccessAt,
            Instant nextDueAt, String lastErrorCode, int attemptsInWindow, int weeklyAttemptLimit) {}
    public record RefreshResult(boolean attempted, String outcome, OperationalStatus state) {}
}
