package com.fashion.recommendation.trend;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ConfiguredJsonTrendSourceAdapterTest {
    private static final String ENDPOINT = "https://trends.test/feed.json";
    private static final String VALID_FEED = """
            {"items":[{
              "id":"urban-layering",
              "platform":"licensed-feed",
              "title":"轻机能通勤的层次感",
              "topicTags":["通勤","轻机能"],
              "heatScore":92,
              "publishedAt":"2026-07-21T08:00:00Z",
              "sourceUrl":"https://source.example/trends/urban-layering",
              "imageUrl":"https://source.example/images/urban-layering.jpg"
            }]}
            """;

    private MockRestServiceServer server;
    private ConfiguredJsonTrendSourceAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new ConfiguredJsonTrendSourceAdapter(
                builder.build(), new ObjectMapper(), ENDPOINT, "licensed-feed", Duration.ofMinutes(15));
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void parsesAValidStrictJsonFeed() {
        server.expect(once(), requestTo(equalTo(ENDPOINT)))
                .andRespond(withSuccess(VALID_FEED, MediaType.APPLICATION_JSON));
        Instant beforeFetch = Instant.now();

        List<TrendItem> result = adapter.fetchPublicSnapshots();

        Instant afterFetch = Instant.now();
        assertEquals("licensed-feed", adapter.platform());
        assertEquals(1, result.size());
        TrendItem item = result.get(0);
        assertEquals("urban-layering", item.id());
        assertEquals("licensed-feed", item.platform());
        assertEquals("轻机能通勤的层次感", item.title());
        assertEquals(List.of("通勤", "轻机能"), item.topicTags());
        assertEquals(92, item.heatScore());
        assertEquals(Instant.parse("2026-07-21T08:00:00Z"), item.publishedAt());
        assertFalse(item.fetchedAt().isBefore(beforeFetch));
        assertFalse(item.fetchedAt().isAfter(afterFetch));
        assertEquals("https://source.example/trends/urban-layering", item.sourceUrl());
        assertEquals("https://source.example/images/urban-layering.jpg", item.imageUrl());
        assertFalse(item.stale());
    }

    @Test
    void cachesAValidatedFeedForTheConfiguredTtl() {
        server.expect(once(), requestTo(equalTo(ENDPOINT)))
                .andRespond(withSuccess(VALID_FEED, MediaType.APPLICATION_JSON));

        List<TrendItem> first = adapter.fetchPublicSnapshots();
        List<TrendItem> second = adapter.fetchPublicSnapshots();

        assertSame(first, second);
    }

    @ParameterizedTest(name = "rejects strict feed violation: {0}")
    @MethodSource("invalidStrictFeeds")
    void rejectsExtraFieldsAndEmptyFeeds(String caseName, String response) {
        server.expect(once(), requestTo(equalTo(ENDPOINT)))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertThrows(TrendSourceException.class, adapter::fetchPublicSnapshots);
    }

    private static Stream<Arguments> invalidStrictFeeds() {
        return Stream.of(
                Arguments.of("root extra field", """
                        {"items":[],"nextPage":"unexpected"}
                        """),
                Arguments.of("item extra field", """
                        {"items":[{
                          "id":"urban-layering",
                          "platform":"licensed-feed",
                          "title":"轻机能通勤的层次感",
                          "topicTags":["通勤"],
                          "heatScore":92,
                          "publishedAt":"2026-07-21T08:00:00Z",
                          "sourceUrl":"https://source.example/trends/urban-layering",
                          "imageUrl":null,
                          "extra":"unexpected"
                        }]}
                        """),
                Arguments.of("empty items", """
                        {"items":[]}
                        """));
    }
}
