package com.fashion.recommendation.trend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class BailianTrendModerationClientTest {
    private ObjectMapper objectMapper;
    private BailianTrendModerationClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        client = new BailianTrendModerationClient(
                null,
                objectMapper,
                "/compatible-mode/v1/chat/completions",
                "test-key",
                "qwen-plus-test",
                true);
    }

    @Test
    void disabledFlagBlocksCallsEvenWhenKeyIsPresent() {
        RestClient restClient = mock(RestClient.class);
        BailianTrendModerationClient disabled = new BailianTrendModerationClient(
                restClient,
                objectMapper,
                "/compatible-mode/v1/chat/completions",
                "present-key",
                "qwen-plus-test",
                false);

        TrendModerationException exception = assertThrows(
                TrendModerationException.class,
                () -> disabled.review(item()));

        assertEquals(TrendModerationFailureReason.AI_DISABLED, exception.reason());
        verifyNoInteractions(restClient);
    }

    @Test
    void sendsOnlyBoundedExternalMetadataToTheProvider() throws Exception {
        JsonNode request = client.buildRequest(item());
        JsonNode userPrompt = objectMapper.readTree(
                request.path("messages").path(1).path("content").asText());

        assertEquals("qwen-plus-test", request.path("model").asText());
        assertEquals("json_object", request.path("response_format").path("type").asText());
        assertEquals("xiaohongshu", userPrompt.path("platform").asText());
        assertEquals("春日通勤", userPrompt.path("title").asText());
        assertEquals(List.of("通勤", "轻薄"), objectMapper.convertValue(
                userPrompt.path("topicTags"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        assertEquals("摘要", userPrompt.path("summary").asText());
        assertEquals("作者", userPrompt.path("author").asText());
        assertTrue(userPrompt.path("hasImage").asBoolean());
        assertTrue(userPrompt.path("evidence").isMissingNode());
        assertTrue(userPrompt.path("likes").isMissingNode());
        assertTrue(request.path("messages").path(0).path("content").asText().contains("只返回一个 JSON 对象"));
    }

    @Test
    void parsesStrictReviewJsonAndRecordsProviderMetadataOnlyForWrappedResponse() throws Exception {
        String content = """
                {"decision":"PASS","riskLevel":"LOW","reason":"与穿搭内容相关"}
                """;
        TrendAiReviewResult direct = client.parseContent(content);
        var response = objectMapper.createObjectNode();
        response.put("id", "chatcmpl-trend-1");
        response.put("model", "qwen-plus");
        response.putArray("choices").addObject().putObject("message").put("content", content);

        TrendAiReviewResult wrapped = client.parseResponse(response.toString());

        assertEquals("PASS", direct.decision());
        assertEquals(null, direct.promptVersion());
        assertEquals("chatcmpl-trend-1", wrapped.providerCallId());
        assertEquals("qwen-plus", wrapped.modelName());
        assertEquals(BailianTrendModerationClient.PROMPT_VERSION, wrapped.promptVersion());
    }

    @Test
    void rejectsExtraFieldsAndUnsupportedDecision() {
        assertThrows(RuntimeException.class, () -> client.parseContent(
                "{\"decision\":\"PASS\",\"riskLevel\":\"LOW\",\"reason\":\"ok\",\"extra\":\"no\"}"));
        assertThrows(RuntimeException.class, () -> client.parseContent(
                "{\"decision\":\"PUBLISH\",\"riskLevel\":\"LOW\",\"reason\":\"no\"}"));
    }

    private static TrendItem item() {
        return new TrendItem(
                "xiaohongshu:test-moderation",
                "xiaohongshu",
                "春日通勤",
                List.of("通勤", "轻薄"),
                82,
                Instant.parse("2026-09-14T08:00:00Z"),
                Instant.parse("2026-09-15T08:00:00Z"),
                "https://example.com/trend",
                false,
                "https://example.com/image.jpg",
                "摘要",
                new TrendEvidence("作者", "image", List.of("https://private.example/image"),
                        100L, 200L, 30L, 10L, "平台评分", null));
    }
}
