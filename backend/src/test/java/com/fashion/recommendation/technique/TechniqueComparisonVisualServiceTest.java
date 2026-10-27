package com.fashion.recommendation.technique;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.recommendation.BailianImageGenerationClient;
import com.fashion.recommendation.storage.StoredImageData;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class TechniqueComparisonVisualServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-31T15:59:59Z");
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private TechniqueComparisonVisualRepository repository;
    private final OutfitTechniqueRepository techniques = mock(OutfitTechniqueRepository.class);
    private final CuratedTechniqueCatalog curated = mock(CuratedTechniqueCatalog.class);
    private final TechniqueSourceCollector collector = mock(TechniqueSourceCollector.class);
    private final TechniqueComparisonImageClient generator = mock(TechniqueComparisonImageClient.class);
    private final Clock clock = mock(Clock.class);
    private TechniqueComparisonVisualService service;

    @BeforeEach
    void setup() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:visual_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V15__technique_comparison_visuals.sql")).execute(data);
        jdbc = new JdbcTemplate(data);
        manager = new DataSourceTransactionManager(data);
        repository = new TechniqueComparisonVisualRepository(jdbc, new ObjectMapper().findAndRegisterModules(), manager);
        when(clock.instant()).thenReturn(NOW);
        when(techniques.latest()).thenReturn(List.of());
        when(curated.items()).thenReturn(List.of());
        when(collector.collectSources(anyList())).thenReturn(List.of());
        when(generator.available()).thenReturn(true);
        when(generator.generate(any(), any())).thenAnswer(invocation -> {
            BooleanSupplier reserve = invocation.getArgument(1);
            if (!reserve.getAsBoolean()) throw new IllegalStateException("budget-unavailable");
            return new StoredImageData(new byte[] {1, 2, 3}, "image/png");
        });
        service = new TechniqueComparisonVisualService(techniques, curated, repository, collector, generator, Runnable::run, clock);
    }

    @Test
    void originalComparisonWinsWithoutCallingOrChargingTheImageModel() {
        var item = technique("source");
        when(curated.items()).thenReturn(List.of(item));
        when(collector.collectSources(anyList())).thenReturn(List.of(new TechniqueArticle("source-1", "腰线对照教程",
                "https://publisher.test/lesson", "作者", "站点", null, NOW, "正文",
                List.of(new TechniqueArticle.ComparisonImage("https://publisher.test/comparison.jpg", "腰线调整前后", "腰线调整前后", "腰线比较")))));
        service.backfill();
        var visual = service.decorate(item).comparisonVisual();
        assertEquals("source", visual.kind());
        assertEquals("https://publisher.test/comparison.jpg", visual.images().get(0).url());
        assertEquals(0, service.budget().used());
        verifyNoInteractions(generator);
    }

    @Test
    void missingOriginalGeneratesOneStoredIllustrationAndReadsNeverGenerate() {
        var item = technique("generated");
        when(curated.items()).thenReturn(List.of(item));
        assertNull(service.decorate(item).comparisonVisual());
        verifyNoInteractions(generator, collector);
        service.backfill();
        var visual = service.decorate(item).comparisonVisual();
        assertEquals("generated", visual.kind());
        assertTrue(visual.note().contains("AI"));
        assertTrue(visual.images().get(0).url().startsWith("/api/v1/outfit-techniques/comparison-images/"));
        service.backfill();
        verify(generator, times(1)).generate(any(), any());
        assertEquals(1, service.budget().used());
        var withoutExample = new OutfitTechnique("plain", "配色", "没有对照的课文", "说明", List.of(), List.of(), "", List.of(), "",
                null, "", "", "", null, "curated", null);
        when(curated.items()).thenReturn(List.of(withoutExample));
        service.backfill();
        verify(generator, times(1)).generate(any(), any());
    }

    @Test
    void unavailableOrIncompatibleImageConfigurationDoesNotConsumeTheMonthlyBudget() {
        when(curated.items()).thenReturn(List.of(technique("unsupported")));
        when(generator.available()).thenReturn(false);
        service.backfill();
        assertEquals(0, service.budget().used());
        verify(generator, never()).generate(any(), any());
    }

    @Test
    void occupiedSharedImagePermitPreservesBudgetAndAllowsRetryAfterRelease() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var images = new BailianImageGenerationClient(new ObjectMapper(), null, new DefaultResourceLoader(), null,
                "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                "https://dashscope.aliyuncs.com/api/v1/tasks", "test-key", "wan2.6-image", true,
                "unused", "unused", "", Duration.ofSeconds(1), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofMillis(1));
        ReflectionTestUtils.setField(images, "restClient", builder.build());
        var permit = (Semaphore) ReflectionTestUtils.getField(images, "generationPermit");
        var items = java.util.stream.IntStream.range(0, 5).mapToObj(i -> technique("busy-" + i)).toList();
        when(curated.items()).thenReturn(items);
        service = new TechniqueComparisonVisualService(techniques, curated, repository, collector,
                new TechniqueComparisonImageClient(images), Runnable::run, clock);

        // This is the same permit held by an ordinary user's in-flight outfit generation.
        assertTrue(permit.tryAcquire());
        try {
            service.backfill();
            assertEquals(0, service.budget().used(), "Local contention must not consume the monthly budget");
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM technique_visual_attempts", Integer.class));
            server.verify();
        } finally { permit.release(); }

        for (int i = 0; i < items.size(); i++) {
            var request = server.expect(requestTo("https://dashscope.aliyuncs.com/api/v1/services/aigc/image-generation/generation"));
            if (i == 0) request.andRespond(ignored -> { throw new java.net.SocketTimeoutException("provider-timeout"); });
            else request.andRespond(withServerError());
        }
        service.backfill();
        assertEquals(items.size(), service.budget().used());
        assertEquals(items.size(), jdbc.queryForObject(
                "SELECT COUNT(*) FROM technique_visual_attempts WHERE outcome='FAILED'", Integer.class));
        // Submitted timeouts and HTTP failures retain their reservation and cannot submit again this month.
        service.backfill();
        server.verify();
        assertEquals(items.size(), service.budget().used());
        assertEquals(1, permit.availablePermits());
    }

    @Test
    void failureAndUnfinishedReservationSurviveRestartsAndCannotRetryInTheSameMonth() {
        var item = technique("failed");
        when(curated.items()).thenReturn(List.of(item));
        doAnswer(invocation -> {
            BooleanSupplier reserve = invocation.getArgument(1);
            reserve.getAsBoolean();
            throw new IllegalStateException("timeout");
        }).when(generator).generate(any(), any());
        service.backfill();
        assertEquals(1, service.budget().used());
        var restarted = new TechniqueComparisonVisualRepository(jdbc, new ObjectMapper().findAndRegisterModules(), manager);
        assertTrue(restarted.reserve(item, NOW).isEmpty());
        var crashed = technique("crashed");
        assertTrue(repository.reserve(crashed, NOW).isPresent());
        assertTrue(restarted.reserve(crashed, NOW).isEmpty());
        assertEquals(2, restarted.budget(NOW).used());
        assertNull(service.decorate(item).comparisonVisual());
    }

    @Test
    void concurrentInstancesReserveAtMostTenAndEachTechniqueAtMostOnce() throws Exception {
        var pool = Executors.newFixedThreadPool(12);
        try {
            var jobs = new ArrayList<java.util.concurrent.Callable<Boolean>>();
            for (int i = 0; i < 32; i++) {
                int id = i;
                jobs.add(() -> new TechniqueComparisonVisualRepository(jdbc, new ObjectMapper(), manager)
                        .reserve(technique("parallel-" + id), NOW).isPresent());
            }
            long accepted = 0;
            for (var future : pool.invokeAll(jobs)) if (future.get(10, TimeUnit.SECONDS)) accepted++;
            assertEquals(10, accepted);
        } finally { pool.shutdownNow(); }
        assertEquals(10, repository.budget(NOW).used());
        assertTrue(repository.reserve(technique("extra"), NOW).isEmpty());
        Instant nextMonth = Instant.parse("2026-10-31T16:00:00Z");
        assertEquals("2026-11", repository.budget(nextMonth).month());
        assertEquals(0, repository.budget(nextMonth).used());
        assertTrue(repository.reserve(technique("extra"), nextMonth).isPresent());
        assertTrue(repository.reserve(technique("extra"), nextMonth).isEmpty());
        assertEquals(10, repository.budget(NOW).used());
    }

    @Test
    void aLateSuccessRemainsChargedToItsOriginalMonth() {
        var item = technique("late");
        var token = repository.reserve(item, NOW).orElseThrow();
        Instant nextMonth = Instant.parse("2026-10-31T16:01:00Z");
        repository.finish(token, item, new StoredImageData(new byte[] {1}, "image/png"), nextMonth);
        assertEquals(1, repository.budget(NOW).used());
        assertEquals(0, repository.budget(nextMonth).used());
        assertTrue(repository.reserve(item, nextMonth).isEmpty());
    }

    @Test
    void aLateOldWorkerCannotOverwriteANewerOriginalComparison() {
        var old = technique("versioned");
        String token = repository.reserve(old, NOW).orElseThrow();
        var current = new OutfitTechnique(old.id(), old.category(), old.title() + "新版", old.summary(), old.sources(), old.sourceNotes(),
                old.synthesisNote(), old.steps(), old.reason(), old.example(), old.suitable(), old.limit(), old.check(),
                old.publishedAt(), old.generationMode(), old.modelName());
        var original = new OutfitTechnique.ComparisonVisual("source", List.of(
                new OutfitTechnique.ComparisonVisual.Image("https://publisher.test/new.jpg", "原文腰线对照")),
                "https://publisher.test/lesson", "原文对照");
        repository.source(current, original);
        repository.finish(token, old, new StoredImageData(new byte[] {1}, "image/png"), NOW);
        assertEquals(original, repository.decorate(current).comparisonVisual());
        assertEquals("generated", repository.decorate(old).comparisonVisual().kind());
        assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT outcome FROM technique_visual_attempts WHERE id=?", String.class, token));
    }

    @Test
    void aLateOldSourceCannotOverwriteANewerVersionsOriginal() {
        var old = technique("source-versioned");
        var current = new OutfitTechnique(old.id(), old.category(), old.title() + "新版", old.summary(), old.sources(), old.sourceNotes(),
                old.synthesisNote(), old.steps(), old.reason(), old.example(), old.suitable(), old.limit(), old.check(),
                old.publishedAt(), old.generationMode(), old.modelName());
        var oldSource = new OutfitTechnique.ComparisonVisual("source", List.of(
                new OutfitTechnique.ComparisonVisual.Image("https://publisher.test/old.jpg", "旧版腰线对照")),
                "https://publisher.test/lesson", "原文对照");
        var currentSource = new OutfitTechnique.ComparisonVisual("source", List.of(
                new OutfitTechnique.ComparisonVisual.Image("https://publisher.test/new.jpg", "新版腰线对照")),
                "https://publisher.test/lesson", "原文对照");
        repository.source(current, currentSource);
        repository.source(old, oldSource);
        assertEquals(currentSource, repository.decorate(current).comparisonVisual());
        assertEquals(oldSource, repository.decorate(old).comparisonVisual());
        assertEquals(0, repository.budget(NOW).used());
    }

    @Test
    void aLaterContentVersionCanGenerateWithoutLosingItsPreviousVisual() {
        var old = technique("updated-content");
        String oldToken = repository.reserve(old, NOW).orElseThrow();
        repository.finish(oldToken, old, new StoredImageData(new byte[] {1}, "image/png"), NOW);
        var current = new OutfitTechnique(old.id(), old.category(), old.title() + "新版", old.summary(), old.sources(), old.sourceNotes(),
                old.synthesisNote(), old.steps(), old.reason(), old.example(), old.suitable(), old.limit(), old.check(),
                old.publishedAt(), old.generationMode(), old.modelName());
        assertTrue(repository.reserve(current, NOW).isEmpty());
        Instant nextMonth = Instant.parse("2026-10-31T16:00:00Z");
        String currentToken = repository.reserve(current, nextMonth).orElseThrow();
        repository.finish(currentToken, current, new StoredImageData(new byte[] {2}, "image/png"), nextMonth);
        assertTrue(repository.decorate(current).comparisonVisual().images().get(0).url().endsWith(currentToken));
        assertTrue(repository.decorate(old).comparisonVisual().images().get(0).url().endsWith(oldToken));
        assertEquals(1, repository.budget(NOW).used());
        assertEquals(1, repository.budget(nextMonth).used());
    }

    @Test
    void anUnrelatedOriginalComparisonDoesNotMasqueradeAsTheLessonExample() {
        var item = technique("unrelated");
        when(curated.items()).thenReturn(List.of(item));
        when(collector.collectSources(anyList())).thenReturn(List.of(new TechniqueArticle("source-1", "长篇教程",
                "https://publisher.test/lesson", null, null, null, NOW, "腰线与其他穿搭方法",
                List.of(new TechniqueArticle.ComparisonImage("https://publisher.test/collar.jpg", "领口前后", "领口对照", "腰线及全篇其他内容")))));
        service.backfill();
        assertEquals("generated", service.decorate(item).comparisonVisual().kind());
        verify(generator).generate(any(), any());
    }

    @Test
    void englishVariableMatchingDoesNotReadWaistcoatAsWaistline() {
        var item = technique("unrelated-word");
        var article = new TechniqueArticle("source-1", "长篇教程", "https://publisher.test/lesson", null, null, null, NOW, "正文",
                List.of(new TechniqueArticle.ComparisonImage("https://publisher.test/waistcoat.jpg", "Before and after waistcoat",
                        "Compare waistcoat colours", "腰线为文章另一个主题")));
        assertNull(TechniqueComparisonVisualService.original(item, List.of(article)));
    }

    static OutfitTechnique technique(String id) {
        return new OutfitTechnique(id, "比例", "腰线调整前后对照", "比较同套衣物的腰线", List.of(
                new OutfitTechnique.Source("source-1", "腰线教程", "https://publisher.test/lesson", null, null, null, null, "正文")),
                List.of(new OutfitTechnique.SourceNote("source-1", "调整腰线")), "", List.of(), "",
                new OutfitTechnique.Example("调整前腰线模糊", "调整后腰线清晰"), "", "", "", null, "curated", null);
    }
}
