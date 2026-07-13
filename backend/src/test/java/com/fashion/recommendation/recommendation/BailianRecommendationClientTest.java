package com.fashion.recommendation.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.style.StyleProfile;
import com.fashion.recommendation.weather.WeatherSnapshot;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BailianRecommendationClientTest {
    private ObjectMapper objectMapper;
    private BailianRecommendationClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        client = new BailianRecommendationClient(null, objectMapper, "/compatible-mode/v1/chat/completions",
                "test-key", "qwen-plus-test");
    }

    @Test
    void buildsJsonOnlyRequestWithWardrobeWeatherOccasionAndStyleProfile() throws Exception {
        LlmRecommendationContext context = context();

        JsonNode request = client.buildRequest(context);
        JsonNode userPrompt = objectMapper.readTree(client.buildUserPrompt(context));

        assertEquals("qwen-plus-test", request.path("model").asText());
        assertEquals("json_object", request.path("response_format").path("type").asText());
        assertEquals("system", request.path("messages").path(0).path("role").asText());
        assertTrue(request.path("messages").path(0).path("content").asText().contains("只返回一个 JSON 对象"));
        assertEquals("user", request.path("messages").path(1).path("role").asText());
        assertEquals(userPrompt, objectMapper.readTree(request.path("messages").path(1).path("content").asText()));
        assertEquals("通勤", userPrompt.path("occasion").asText());
        assertEquals("极简", userPrompt.path("styleHint").asText());
        assertEquals(26.0, userPrompt.path("weather").path("temperatureC").asDouble());
        assertEquals("复古", userPrompt.path("styleProfile").path("stylePreferences").path(0).asText());
        assertEquals(2, userPrompt.path("wardrobe").size());
        assertEquals(11L, userPrompt.path("wardrobe").path(0).path("id").asLong());
        assertEquals("米白衬衫", userPrompt.path("wardrobe").path(0).path("name").asText());
    }

    @Test
    void parsesStrictContentAndWrappedResponse() throws Exception {
        String content = """
                {"summary":"  极简通勤  ","reason":"  适合当前天气  ","itemIds":[11,12]}
                """;

        LlmRecommendationResult direct = client.parseContent(content);
        var response = objectMapper.createObjectNode();
        response.putArray("choices").addObject().putObject("message").put("content", content);
        LlmRecommendationResult wrapped = client.parseResponse(response.toString());

        assertEquals("极简通勤", direct.summary());
        assertEquals("适合当前天气", direct.reason());
        assertEquals(List.of(11L, 12L), direct.itemIds());
        assertEquals(direct, wrapped);
    }

    @ParameterizedTest(name = "rejects constrained JSON violation: {0}")
    @MethodSource("constrainedJsonViolations")
    void rejectsExtraFieldsStringIdsAndDuplicateIds(String caseName, String content) {
        assertThrows(RuntimeException.class, () -> client.parseContent(content));
    }

    @ParameterizedTest(name = "rejects non-JSON model content: {0}")
    @MethodSource("nonJsonContent")
    void rejectsCodeFencesAndInvalidJson(String caseName, String content) {
        assertThrows(JsonProcessingException.class, () -> client.parseContent(content));
    }

    private static Stream<Arguments> constrainedJsonViolations() {
        return Stream.of(
                Arguments.of("extra field", """
                        {"summary":"通勤","reason":"适合当前天气","itemIds":[11,12],"extra":"not allowed"}
                        """),
                Arguments.of("string ID", """
                        {"summary":"通勤","reason":"适合当前天气","itemIds":["11",12]}
                        """),
                Arguments.of("duplicate ID", """
                        {"summary":"通勤","reason":"适合当前天气","itemIds":[11,11]}
                        """));
    }

    private static Stream<Arguments> nonJsonContent() {
        return Stream.of(
                Arguments.of("Markdown fence", """
                        ```json
                        {"summary":"通勤","reason":"适合当前天气","itemIds":[11,12]}
                        ```
                        """),
                Arguments.of("malformed JSON", """
                        {"summary":"通勤","reason":,"itemIds":[11,12]}
                        """),
                Arguments.of("trailing JSON", """
                        {"summary":"通勤","reason":"适合当前天气","itemIds":[11,12]} {}
                        """));
    }

    private static LlmRecommendationContext context() {
        Instant observedAt = Instant.parse("2026-07-13T02:00:00Z");
        List<WardrobeItem> wardrobe = List.of(
                new WardrobeItem(11L, "米白衬衫", "上装", "暖白", "极简", null, observedAt),
                new WardrobeItem(12L, "深蓝直筒裤", "下装", "深蓝", null, null, observedAt));
        WeatherSnapshot weather = new WeatherSnapshot(
                "长沙", 26.0, 27.5, 0.0, 1, 10.0, observedAt, "test-weather");
        StyleProfile profile = new StyleProfile(
                "小夏", List.of("复古"), List.of("深蓝"), List.of("通勤"),
                List.of("复古"), List.of("法式休闲"), List.of("深蓝"), List.of("直筒下装"),
                "已生成风格档案。", "qwen-plus-test", observedAt, false);
        return new LlmRecommendationContext("通勤", "极简", wardrobe, weather, profile);
    }
}
