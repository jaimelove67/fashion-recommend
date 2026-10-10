package com.fashion.recommendation.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.style.PersonalStyleAnalysis;
import com.fashion.recommendation.style.StyleProfile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class PersonalProfilePromptTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final BailianRecommendationClient client = new BailianRecommendationClient(
            RestClient.create(), mapper, "http://127.0.0.1:1/chat", "test-key", "test-model", true);

    @Test
    void includesConfirmedAnalysisAndMeasurementsWithoutPrivatePhotoOrStorageMetadata() throws Exception {
        var input = mapper.readTree(client.buildUserPrompt(context(false))).path("styleProfile");
        assertEquals(175, input.path("heightCm").asDouble());
        assertEquals(65, input.path("weightKg").asDouble());
        assertEquals("人工确认的方脸", input.path("analysis").path("faceShape").asText());
        assertEquals("V领", input.path("analysis").path("fitSuggestions").get(0).asText());
        assertFalse(input.has("photoUrl"));
        assertFalse(input.has("photoObjectKey"));
        assertFalse(input.has("revision"));
        assertFalse(input.has("analysisModelName"));
    }

    @Test
    void excludesOutdatedAnalysisAndDerivedAdviceButKeepsCurrentMeasurementsAndExplicitPreferences() throws Exception {
        var input = mapper.readTree(client.buildUserPrompt(context(true))).path("styleProfile");
        assertTrue(input.path("stale").asBoolean());
        assertEquals(175, input.path("heightCm").asDouble());
        assertEquals("通勤", input.path("stylePreferences").get(0).asText());
        for (String field : List.of("analysis", "styleTags", "tryStyleTags", "colorSuggestions", "itemSuggestions", "reasonSummary"))
            assertFalse(input.has(field), "Outdated field must not enter recommendation: " + field);
    }

    @Test
    void sendsConfirmationAndAvoidConditionsSeparatelyFromAnalysisSuggestions() throws Exception {
        LlmRecommendationContext base = context(false);
        StyleProfile profile = base.styleProfile().withPreferenceDetails(List.of("紧身"), true);
        var input = mapper.readTree(client.buildUserPrompt(new LlmRecommendationContext("通勤", "", List.of(), null, profile, Map.of()))).path("styleProfile");
        assertTrue(input.path("preferencesConfirmed").asBoolean());
        assertEquals("紧身", input.path("avoidPreferences").get(0).asText());
        assertEquals("通勤", input.path("stylePreferences").get(0).asText());
        assertEquals("分析派生风格", input.path("styleTags").get(0).asText());
    }

    private static LlmRecommendationContext context(boolean stale) {
        var analysis = new PersonalStyleAnalysis("人工确认的方脸", "", "", "", "", List.of("V领"), List.of("利落"),
                List.of(), List.of("米白"), List.of(), "穿衣参考");
        var profile = new StyleProfile("用户", "MALE", List.of("通勤"), List.of("米白"), List.of("通勤"),
                List.of("分析派生风格"), List.of("探索风格"), List.of("分析派生颜色"), List.of("分析派生单品"),
                "分析派生说明", "manual-profile", Instant.now(), stale, 175.0, 65.0,
                "/api/v1/me/style-profile/photo?v=2", "private/photo.png", analysis, "MANUAL", null, Instant.now(), 2);
        return new LlmRecommendationContext("通勤", "利落", List.of(), null, profile, Map.of());
    }
}
