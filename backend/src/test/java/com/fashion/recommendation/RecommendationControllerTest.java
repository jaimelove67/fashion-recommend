package com.fashion.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.recommendation.LlmRecommendationClient;
import com.fashion.recommendation.recommendation.LlmRecommendationContext;
import com.fashion.recommendation.recommendation.LlmRecommendationResult;
import com.fashion.recommendation.recognition.GarmentRecognitionService;
import com.fashion.recommendation.storage.ImageStorage;
import com.fashion.recommendation.storage.StoredImage;
import com.fashion.recommendation.weather.WeatherService;
import com.fashion.recommendation.weather.WeatherSnapshot;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.ResourceAccessException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc
class RecommendationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WeatherService weatherService;

    @MockBean
    private LlmRecommendationClient llmRecommendationClient;

    @MockBean
    private ImageStorage imageStorage;

    @MockBean
    private GarmentRecognitionService garmentRecognitionService;

    @BeforeEach
    void setUpWeather() {
        given(weatherService.current(anyString())).willAnswer(invocation -> new WeatherSnapshot(
                invocation.getArgument(0), 26.0, 27.5, 0.0, 1, 10.0,
                java.time.Instant.parse("2026-07-13T02:00:00Z"), "test-weather"));
        given(llmRecommendationClient.recommend(any(LlmRecommendationContext.class))).willReturn(Optional.empty());
        given(garmentRecognitionService.recognize(any())).willReturn(Optional.empty());
    }

    @Test
    void generatesSavesAndProtectsRecommendationByUser() throws Exception {
        String userId = "recommendation-user";
        long topId = createItem(userId, "米白衬衫", "上装", "暖白");
        createItem(userId, "黑色直筒裤", "下装", "石墨灰");
        createItem(userId, "低跟皮鞋", "鞋履", "黑色");

        var generated = mockMvc.perform(post("/api/v1/recommendations")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"通勤","city":"长沙","temperatureC":26.0,"styleHint":"极简"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.engine").value("development-rule-v1"))
                .andExpect(jsonPath("$.data.temperatureC").value(26.0))
                .andExpect(jsonPath("$.data.weather.source").value("test-weather"))
                .andExpect(jsonPath("$.data.saved").value(false))
                .andReturn();
        long recommendationId = readData(generated).path("id").asLong();

        mockMvc.perform(post("/api/v1/me/recommendations/" + recommendationId + "/save")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(true));

        mockMvc.perform(post("/api/v1/me/recommendations/" + recommendationId + "/feedback")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""" 
                                {"rating":5,"feedbackType":"useful","comment":"适合通勤"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback.rating").value(5))
                .andExpect(jsonPath("$.data.feedback.comment").value("适合通勤"));

        mockMvc.perform(get("/api/v1/me/recommendations").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].saved").value(true))
                .andExpect(jsonPath("$.data[0].feedback.rating").value(5));

        mockMvc.perform(get("/api/v1/recommendations/" + recommendationId)
                        .header("X-User-Id", "another-user"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(delete("/api/v1/me/wardrobe/" + topId).header("X-User-Id", userId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/recommendations/" + recommendationId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].name").value("米白衬衫"));
    }

    @Test
    void refusesRecommendationWhenWardrobeCannotFormAnOutfit() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations")
                        .header("X-User-Id", "empty-wardrobe-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"周末","city":"长沙"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void refusesRecommendationWhenAllItemsShareOneCategory() throws Exception {
        String userId = "single-category-user";
        createItem(userId, "米白衬衫", "上装", "暖白");
        createItem(userId, "灰色卫衣", "上装", "石墨灰");

        mockMvc.perform(post("/api/v1/recommendations")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"通勤","city":"长沙"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void ruleEnginePrefersItemsWithHigherHistoricalFeedback() throws Exception {
        String userId = "feedback-loop-user";
        long preferredTopId = createItem(userId, "酒红针织衫", "上装", "酒红");
        createItem(userId, "深蓝直筒裤", "下装", "深蓝");

        var first = mockMvc.perform(post("/api/v1/recommendations")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"通勤","city":"长沙"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("development-rule-v1"))
                .andReturn();
        long firstId = readData(first).path("id").asLong();

        mockMvc.perform(post("/api/v1/me/recommendations/" + firstId + "/feedback")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":5,"feedbackType":"useful"}
                                """))
                .andExpect(status().isOk());

        long newerTopId = createItem(userId, "灰色卫衣", "上装", "石墨灰");

        mockMvc.perform(post("/api/v1/recommendations")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"通勤","city":"长沙"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("development-rule-v1"))
                .andExpect(jsonPath("$.data.items[0].id").value((int) preferredTopId));

        assertTrue(newerTopId > preferredTopId, "newer item should sort first without the feedback signal");
    }

    @Test
    void deletesWardrobeItemForTheCurrentUser() throws Exception {
        String userId = "delete-wardrobe-user";
        long itemId = createItem(userId, "灰色卫衣", "上装", "灰色");

        mockMvc.perform(delete("/api/v1/me/wardrobe/" + itemId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/me/wardrobe").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void persistsStylePreferencesAndIgnoresClientTemperature() throws Exception {
        String userId = "profile-user";
        mockMvc.perform(post("/api/v1/me/style-profile/refresh")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"小林","stylePreferences":["复古","通勤"],"colorPreferences":["酒红","深蓝"],"occasions":["约会","通勤"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("小林"))
                .andExpect(jsonPath("$.data.colorPreferences[0]").value("酒红"));

        mockMvc.perform(get("/api/v1/me/style-profile").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stylePreferences[0]").value("复古"))
                .andExpect(jsonPath("$.data.occasions[0]").value("约会"));

        createItem(userId, "针织衫", "上装", "酒红");
        createItem(userId, "半身裙", "下装", "深蓝");
        mockMvc.perform(post("/api/v1/recommendations")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"约会","city":"长沙","temperatureC":-10.0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temperatureC").value(26.0))
                .andExpect(jsonPath("$.data.reason").value(org.hamcrest.Matchers.containsString("复古、通勤")));
    }

    @Test
    void usesLlmResultAndPassesCompleteRecommendationContext() throws Exception {
        String userId = "llm-success-user";
        long topId = createItem(userId, "米白衬衫", "上装", "暖白");
        long bottomId = createItem(userId, "深蓝直筒裤", "下装", "深蓝");
        createItem(userId, "低跟皮鞋", "鞋履", "黑色");
        mockMvc.perform(post("/api/v1/me/style-profile/refresh")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"小夏","stylePreferences":["复古"],"colorPreferences":["深蓝"],"occasions":["约会"]}
                                """))
                .andExpect(status().isOk());

        given(llmRecommendationClient.recommend(any(LlmRecommendationContext.class))).willReturn(Optional.of(
                new LlmRecommendationResult("深蓝复古穿搭", "色彩呼应风格档案与当前天气。", List.of(topId, bottomId))));

        mockMvc.perform(post("/api/v1/recommendations")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"约会","city":"长沙","styleHint":"法式复古"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("llm"))
                .andExpect(jsonPath("$.data.summary").value("深蓝复古穿搭"))
                .andExpect(jsonPath("$.data.reason").value("色彩呼应风格档案与当前天气。"))
                .andExpect(jsonPath("$.data.items[0].id").value(topId))
                .andExpect(jsonPath("$.data.items[1].id").value(bottomId));

        ArgumentCaptor<LlmRecommendationContext> contextCaptor = ArgumentCaptor.forClass(LlmRecommendationContext.class);
        verify(llmRecommendationClient).recommend(contextCaptor.capture());
        LlmRecommendationContext context = contextCaptor.getValue();
        assertEquals("约会", context.occasion());
        assertEquals("法式复古", context.styleHint());
        assertEquals("长沙", context.weather().city());
        assertEquals(26.0, context.weather().temperatureC());
        assertEquals(List.of("复古"), context.styleProfile().stylePreferences());
        assertEquals(List.of("深蓝"), context.styleProfile().colorPreferences());
        assertEquals(List.of("约会"), context.styleProfile().occasions());
        assertEquals(3, context.wardrobe().size());
        assertTrue(context.wardrobe().stream().anyMatch(item -> item.id().equals(topId)));
        assertTrue(context.wardrobe().stream().anyMatch(item -> item.id().equals(bottomId)));
    }

    @Test
    void rejectsExistingItemFromAnotherUsersWardrobeAndFallsBackAsAWhole() throws Exception {
        String userId = "llm-id-validation-user";
        long topId = createItem(userId, "白色T恤", "上装", "白色");
        long bottomId = createItem(userId, "黑色长裤", "下装", "黑色");
        long shoeId = createItem(userId, "乐福鞋", "鞋履", "棕色");
        long foreignItemId = createItem("llm-id-validation-other-user", "他人外套", "外套", "灰色");
        given(llmRecommendationClient.recommend(any(LlmRecommendationContext.class))).willReturn(Optional.of(
                new LlmRecommendationResult("不应接受的推荐", "包含其他用户的衣物 ID。", List.of(topId, foreignItemId))));

        var result = mockMvc.perform(post("/api/v1/recommendations")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"通勤","city":"长沙"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("development-rule-v1"))
                .andExpect(jsonPath("$.data.summary").value(org.hamcrest.Matchers.not("不应接受的推荐")))
                .andReturn();

        JsonNode items = readData(result).path("items");
        assertEquals(3, items.size());
        List<Long> currentUserItemIds = List.of(topId, bottomId, shoeId);
        items.forEach(item -> assertTrue(currentUserItemIds.contains(item.path("id").asLong())));
        assertFalse(items.findValuesAsText("id").contains(Long.toString(foreignItemId)));
    }

    @Test
    void fallsBackToRuleEngineWhenLlmTimesOut() throws Exception {
        String userId = "llm-exception-user";
        createItem(userId, "针织衫", "上装", "酒红");
        createItem(userId, "半身裙", "下装", "深蓝");
        given(llmRecommendationClient.recommend(any(LlmRecommendationContext.class)))
                .willThrow(new ResourceAccessException("LLM timeout", new SocketTimeoutException("read timed out")));

        mockMvc.perform(post("/api/v1/recommendations")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"约会","city":"长沙"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("development-rule-v1"))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void storesUploadedImageAndAllowsManualCorrectionAfterRecognitionFailure() throws Exception {
        String userId = "image-user";
        given(imageStorage.store(anyString(), any())).willReturn(new StoredImage("wardrobe/image-user/a.png", "image/png"));

        var upload = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                        "/api/v1/me/wardrobe/upload")
                        .file(new MockMultipartFile("image", "shirt.png", "image/png", new byte[] {1, 2, 3}))
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recognitionStatus").value("NEEDS_MANUAL_REVIEW"))
                .andExpect(jsonPath("$.data.category").value("待识别"))
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.containsString("/image?userId=image-user")))
                .andReturn();
        long itemId = readData(upload).path("id").asLong();

        mockMvc.perform(put("/api/v1/me/wardrobe/" + itemId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"米白衬衫","category":"上装","color":"暖白","style":"极简"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recognitionStatus").value("MANUAL_CORRECTED"))
                .andExpect(jsonPath("$.data.name").value("米白衬衫"));

        mockMvc.perform(get("/api/v1/me/wardrobe").header("X-User-Id", "another-image-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        verify(imageStorage).store(eq(userId), any());
    }

    @Test
    void rejectsUnsupportedImageBeforeStorage() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                        "/api/v1/me/wardrobe/upload")
                        .file(new MockMultipartFile("image", "script.svg", "image/svg+xml", new byte[] {1}))
                        .header("X-User-Id", "invalid-image-user"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        org.mockito.Mockito.verifyNoInteractions(imageStorage);
    }

    private long createItem(String userId, String name, String category, String color) throws Exception {
        var result = mockMvc.perform(post("/api/v1/me/wardrobe")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("name", name)
                                .put("category", category)
                                .put("color", color)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readData(result).path("id").asLong();
    }

    private JsonNode readData(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
