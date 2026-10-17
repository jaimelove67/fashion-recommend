package com.fashion.recommendation.technique;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class WeeklyTechniqueServiceTest {
    private static final Instant START = Instant.parse("2026-10-16T02:00:00Z");
    private final TechniqueSourceCollector collector = mock(TechniqueSourceCollector.class);
    private final TechniqueGenerationClient generator = mock(TechniqueGenerationClient.class);
    private final Clock clock = mock(Clock.class);
    private Instant now;
    private OutfitTechniqueRepository repository;
    private WeeklyTechniqueService service;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:technique_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V14__weekly_outfit_techniques.sql")).execute(datasource);
        jdbc = new JdbcTemplate(datasource);
        repository = new OutfitTechniqueRepository(jdbc, new ObjectMapper().findAndRegisterModules(),
                new DataSourceTransactionManager(datasource));
        now = START;
        when(clock.instant()).thenAnswer(ignored -> now);
        when(generator.available()).thenReturn(true);
        when(collector.collect()).thenReturn(articles());
        service = new WeeklyTechniqueService(repository, collector, generator, true, clock);
    }

    @Test
    void disabledOrMissingModelNeverCollectsOrConsumesTheBudget() {
        when(generator.available()).thenReturn(false);
        assertEquals("disabled", service.refresh().outcome());
        assertEquals("disabled", service.currentFeed().status());
        assertEquals(0, service.status().attemptsInWindow());
        verifyNoInteractions(collector);
        verify(generator, never()).generate(anyList(), anyList());

        reset(generator);
        var disabled = new WeeklyTechniqueService(repository, collector, generator, false, clock);
        assertEquals("disabled", disabled.refresh().outcome());
        verifyNoInteractions(generator);
    }

    @Test
    void publishesOnlyOncePerSevenDaysAndSurvivesAServiceRestart() {
        when(generator.generate(anyList(), anyList())).thenReturn(result("weekly-1", "用腰线调整比例", "one", "two"));
        assertEquals("published", service.refresh().outcome());
        assertEquals(1, service.currentFeed().items().size());
        assertEquals(START, service.currentFeed().latestPublishedAt());

        service = new WeeklyTechniqueService(repository, collector, generator, true, clock);
        now = START.plus(Duration.ofDays(7)).minusMillis(1);
        assertFalse(service.refresh().attempted());
        verify(collector, times(1)).collect();
        now = START.plus(Duration.ofDays(7));
        // Reusing one source for a distinct multi-source topic is allowed.
        when(generator.generate(anyList(), anyList())).thenReturn(result("weekly-2", "叠穿的厚薄顺序", "one", "three"));
        assertEquals("published", service.refresh().outcome());
        assertEquals(List.of("叠穿的厚薄顺序", "用腰线调整比例"), repository.recentTitles());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM outfit_technique_attempts WHERE outcome='PUBLISHED'", Integer.class));
    }

    @Test
    void rejectsLowQualityWaitsFortyEightHoursAndCapsTwoAttemptsInRollingWeek() {
        when(generator.generate(anyList(), anyList())).thenThrow(new IllegalArgumentException("private provider details"));
        assertEquals("waiting-for-quality", service.refresh().outcome());
        assertEquals("quality-rejected", service.status().lastErrorCode());
        assertTrue(service.currentFeed().items().isEmpty());
        assertEquals(START.plus(Duration.ofHours(48)), service.status().nextDueAt());

        now = START.plus(Duration.ofHours(48)).minusMillis(1);
        assertFalse(service.refresh().attempted());
        now = START.plus(Duration.ofHours(48));
        assertTrue(service.refresh().attempted());
        assertEquals(2, service.status().attemptsInWindow());
        assertEquals(START.plus(Duration.ofDays(7)), service.status().nextDueAt());
        now = START.plus(Duration.ofDays(7)).minusMillis(1);
        assertFalse(service.refresh().attempted());
        now = START.plus(Duration.ofDays(7));
        doReturn(result("weekly-good", "适度留白的方法", "one", "four")).when(generator).generate(anyList(), anyList());
        assertEquals("published", service.refresh().outcome());
        verify(collector, times(3)).collect();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM outfit_technique_attempts WHERE error_code LIKE '%private%'", Integer.class));
    }

    @Test
    void enforcesScoreAndSourceQualityAgainAtPublication() {
        var valid = result("low-score", "低质量稿件", "one", "two");
        when(generator.generate(anyList(), anyList())).thenReturn(new TechniqueGenerationResult(valid.technique(), 84, "test-call"));
        assertEquals("waiting-for-quality", service.refresh().outcome());
        assertEquals("quality-rejected", service.status().lastErrorCode());
        assertTrue(repository.latest().isEmpty());
        assertEquals(84, jdbc.queryForObject("SELECT quality_score FROM outfit_technique_attempts", Integer.class));
    }

    @Test
    void deduplicatesNormalizedTitlesAndExactSourceSetsWithoutRejectingPartialOverlap() {
        String first = repository.tryAcquire(now).orElseThrow();
        assertTrue(repository.publish(first, result("a", "叠穿：保留 空间", "one", "two"), now));
        now = START.plus(Duration.ofDays(7));
        String duplicateTitle = repository.tryAcquire(now).orElseThrow();
        assertFalse(repository.publish(duplicateTitle, result("b", "叠穿保留空间", "three", "four"), now));
        assertEquals("duplicate-content", repository.state(now).lastErrorCode());
        now = START.plus(Duration.ofDays(9));
        String duplicateSources = repository.tryAcquire(now).orElseThrow();
        assertFalse(repository.publish(duplicateSources, result("c", "完全不同的标题", "two", "one"), now));
        assertEquals("duplicate-content", repository.state(now).lastErrorCode());
        now = START.plus(Duration.ofDays(14));
        String distinct = repository.tryAcquire(now).orElseThrow();
        assertTrue(repository.publish(distinct, result("d", "把握材质对比", "one", "five"), now));
        assertEquals(2, repository.latest().size());
    }

    @Test
    void concurrentWorkersGetOneLeaseAndCrashedWorkerCannotReleaseItsSuccessor() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        List<java.util.concurrent.Future<java.util.Optional<String>>> leases = new ArrayList<>();
        try {
            for (int index = 0; index < 8; index++) leases.add(executor.submit(() -> repository.tryAcquire(START)));
            List<String> winners = new ArrayList<>();
            for (var lease : leases) lease.get(10, TimeUnit.SECONDS).ifPresent(winners::add);
            assertEquals(1, winners.size());
            String oldToken = winners.get(0);
            now = START.plus(Duration.ofMinutes(16));
            assertEquals("lease-expired", repository.state(now).lastErrorCode());
            assertTrue(repository.tryAcquire(now).isEmpty());
            assertEquals(1, repository.state(now).attemptsInWindow());
            now = START.plus(Duration.ofHours(48));
            String successor = repository.tryAcquire(now).orElseThrow();
            repository.fail(oldToken, "source-unavailable", now);
            assertFalse(repository.publish(oldToken, result("old", "旧任务不应发布", "one", "two"), now));
            assertTrue(repository.state(now).collecting());
            assertTrue(repository.publish(successor, result("new", "新任务继续发布", "one", "three"), now));
            assertEquals("lease-expired", jdbc.queryForObject("SELECT error_code FROM outfit_technique_attempts WHERE id=?", String.class, oldToken));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sourceFailurePreservesPublishedFeedAndDoesNotLeakProviderMessages() {
        when(generator.generate(anyList(), anyList())).thenReturn(result("first", "已保存的技巧", "one", "two"));
        assertEquals("published", service.refresh().outcome());
        now = START.plus(Duration.ofDays(7));
        when(collector.collect()).thenThrow(new IllegalStateException("secret-key=do-not-persist"));
        assertEquals("waiting-for-quality", service.refresh().outcome());
        assertEquals("source-unavailable", service.status().lastErrorCode());
        assertEquals(1, service.currentFeed().items().size());
        assertEquals("waiting-for-quality", service.currentFeed().status());
        verify(generator, times(1)).generate(anyList(), anyList());
    }

    private static List<TechniqueArticle> articles() {
        return List.of(new TechniqueArticle("source-one", "Source one", "https://example.com/one", "Author", "Publisher", "2026-10-01", START, "Full article evidence one"),
                new TechniqueArticle("source-two", "Source two", "https://example.com/two", "Author", "Publisher", "2026-10-02", START, "Full article evidence two"));
    }

    private static TechniqueGenerationResult result(String id, String title, String firstSource, String secondSource) {
        List<OutfitTechnique.Source> sources = List.of(source(firstSource), source(secondSource));
        return new TechniqueGenerationResult(new OutfitTechnique(id, "layering", title, "针对不同材质与层次的搭配建议。", sources,
                List.of(new OutfitTechnique.SourceNote(firstSource, "来源要点")), "依据原文整理并保留适用条件。",
                List.of("先观察轮廓。", "再调整层次。"), "减少相互遮挡。", new OutfitTechnique.Example("调整前", "调整后"),
                "日常叠穿", "并非固定比例规则", "检查活动空间", START.minus(Duration.ofDays(10)), "ai-reviewed", "test-model"), 90, "private-call-id");
    }

    private static OutfitTechnique.Source source(String id) {
        return new OutfitTechnique.Source(id, "Article " + id, "https://example.com/" + id, "Author", "Publisher", "2026-10-01", START.toString(), "正文全文");
    }
}
