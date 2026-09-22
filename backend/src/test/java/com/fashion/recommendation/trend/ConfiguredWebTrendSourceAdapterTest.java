package com.fashion.recommendation.trend;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ConfiguredWebTrendSourceAdapterTest {
    private static final URI ENDPOINT = URI.create("https://example.com/fashion/trends");
    private static final MediaType HTML_UTF8 = MediaType.parseMediaType("text/html; charset=UTF-8");
    private static final String HTML = """
            <!doctype html>
            <html><head>
              <title>页面标题不作为主标题</title>
              <meta property="og:title" content="秋季低饱和叠穿趋势">
              <meta property="og:description" content="适合通勤衣橱的秋季层次搭配观察。">
              <meta property="og:image" content="https://cdn.example.com/trend.jpg">
              <meta name="keywords" content="秋季, 叠穿, 通勤">
              <script type="application/ld+json">
                {"@type":"Article","headline":"秋季低饱和叠穿趋势","datePublished":"2026-09-08T08:00:00Z","keywords":["低饱和"],"description":"从公开页面抽取的趋势摘要。"}
              </script>
            </head><body><main><h1>秋季低饱和叠穿趋势</h1></main></body></html>
            """;

    private MockRestServiceServer server;
    private ConfiguredWebTrendSourceAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new ConfiguredWebTrendSourceAdapter(
                builder.build(), new ObjectMapper(), List.of(ENDPOINT), "web-scrape", 100_000, 10,
                Duration.ofMinutes(15));
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void extractsOpenGraphJsonLdAndVisibleFallbackFields() {
        server.expect(once(), requestTo(equalTo(ENDPOINT.toString())))
                .andRespond(withSuccess(HTML, HTML_UTF8));

        List<TrendItem> result = adapter.fetchPublicSnapshots();

        assertEquals(1, result.size());
        TrendItem item = result.get(0);
        assertEquals("web-scrape", item.platform());
        assertEquals("秋季低饱和叠穿趋势", item.title());
        assertTrue(item.topicTags().containsAll(List.of("秋季", "叠穿", "通勤", "低饱和")));
        assertEquals("https://cdn.example.com/trend.jpg", item.imageUrl());
        assertEquals("适合通勤衣橱的秋季层次搭配观察。", item.summary());
        assertEquals("https://example.com/fashion/trends", item.sourceUrl());
        assertEquals("2026-09-08T08:00:00Z", item.publishedAt().toString());
        assertTrue(item.heatScore() >= 0 && item.heatScore() <= 100);
        assertEquals("来源页信号评分", adapter.scoreLabel());
        assertFalse(item.stale());
    }

    @Test
    void extractsSeveralArticleCardsAndUsesTheirLinks() {
        String html = """
                <html><body>
                  <article><a href="/looks/one"><h2>软剪裁日常化</h2><img src="/one.jpg"></a><p>柔和剪裁与基础单品组合。</p></article>
                  <article><a href="/looks/two"><h2>暖白与雾蓝配色</h2><img src="/two.jpg"></a><p>低饱和配色成为秋季线索。</p></article>
                </body></html>
                """;
        server.expect(once(), requestTo(equalTo(ENDPOINT.toString())))
                .andRespond(withSuccess(html, HTML_UTF8));

        List<TrendItem> result = adapter.fetchPublicSnapshots();

        assertEquals(2, result.size());
        assertEquals("https://example.com/looks/one", result.get(0).sourceUrl());
        assertEquals("https://example.com/one.jpg", result.get(0).imageUrl());
        assertEquals("软剪裁日常化", result.get(0).title());
        assertEquals("https://example.com/looks/two", result.get(1).sourceUrl());
    }

    @Test
    void cachesSuccessfulPageUntilTheConfiguredTtlExpires() {
        server.expect(once(), requestTo(equalTo(ENDPOINT.toString())))
                .andRespond(withSuccess(HTML, HTML_UTF8));

        List<TrendItem> first = adapter.fetchPublicSnapshots();
        List<TrendItem> second = adapter.fetchPublicSnapshots();

        assertTrue(first == second);
    }

    @Test
    void failsWhenAllConfiguredPagesAreUnavailable() {
        server.expect(once(), requestTo(equalTo(ENDPOINT.toString())))
                .andRespond(withServerError());

        assertThrows(TrendSourceException.class, adapter::fetchPublicSnapshots);
    }

    @Test
    void keepsSuccessfulPagesWhenAnotherConfiguredPageIsUnavailable() {
        URI secondEndpoint = URI.create("https://example.com/fashion/second");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new ConfiguredWebTrendSourceAdapter(
                builder.build(), new ObjectMapper(), List.of(ENDPOINT, secondEndpoint), "web-scrape",
                100_000, 10, Duration.ofMinutes(15));
        server.expect(once(), requestTo(equalTo(ENDPOINT.toString())))
                .andRespond(withServerError());
        server.expect(once(), requestTo(equalTo(secondEndpoint.toString())))
                .andRespond(withSuccess(HTML, HTML_UTF8));

        List<TrendItem> result = adapter.fetchPublicSnapshots();

        assertEquals(1, result.size());
        assertEquals(secondEndpoint.toString(), result.get(0).sourceUrl());
    }

    @Test
    void rejectsLocalAndNonHttpConfiguredUrls() {
        assertThrows(TrendSourceException.class, () -> new ConfiguredWebTrendSourceAdapter(
                new ObjectMapper(), "http://localhost:8080/trends", "web-scrape", 100_000, 10,
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMinutes(1)));
        assertThrows(TrendSourceException.class, () -> new ConfiguredWebTrendSourceAdapter(
                new ObjectMapper(), "file:///tmp/trends.html", "web-scrape", 100_000, 10,
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMinutes(1)));
    }

    @Test
    void missingOptionalMetadataDoesNotInventImageOrSummary() {
        String html = "<html><head><title>只有标题的网页</title></head><body></body></html>";
        server.expect(once(), requestTo(equalTo(ENDPOINT.toString())))
                .andRespond(withSuccess(html, HTML_UTF8));

        TrendItem item = adapter.fetchPublicSnapshots().get(0);

        assertNull(item.imageUrl());
        assertNull(item.summary());
        assertEquals(List.of("网页趋势"), item.topicTags());
    }
}
