package com.fashion.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.recommendation.LlmRecommendationClient;
import com.fashion.recommendation.recommendation.LlmRecommendationContext;
import com.fashion.recommendation.recommendation.LlmRecommendationResult;
import com.fashion.recommendation.recommendation.BailianImageGenerationClient;
import com.fashion.recommendation.recommendation.OutfitImageGenerationResult;
import com.fashion.recommendation.recognition.GarmentRecognitionResult;
import com.fashion.recommendation.recognition.GarmentRecognitionService;
import com.fashion.recommendation.storage.ImageStorage;
import com.fashion.recommendation.storage.StoredImage;
import com.fashion.recommendation.weather.WeatherService;
import com.fashion.recommendation.weather.WeatherSnapshot;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResourceAccessException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@AutoConfigureMockMvc
class RecommendationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private WeatherService weatherService;

    @MockBean
    private LlmRecommendationClient llmRecommendationClient;

    @MockBean
    private BailianImageGenerationClient bailianImageGenerationClient;

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
    }

    @Autowired
    private com.fashion.recommendation.trend.TrendRepository trendRepository;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void usesStoredTrendReferenceAndRejectsHiddenOrUnknownReferences() throws Exception {
        String userId = "trend-reference-user";
        createItem(userId, "白衬衫", "上装", "白色");
        createItem(userId, "直筒裤", "下装", "黑色");
        var now = java.time.Instant.now();
        trendRepository.save("xiaohongshu", new com.fashion.recommendation.trend.TrendItem(
                "xiaohongshu:reference-test", "xiaohongshu", "极简通勤", List.of("极简", "通勤"), 0,
                now.minusSeconds(60), now, "https://www.xiaohongshu.com/explore/test", false, null));
        assertTrue(trendRepository.markAiReviewed(
                "xiaohongshu:reference-test",
                new com.fashion.recommendation.trend.TrendAiReviewResult(
                        "PASS", "LOW", "测试内容已进入人工终审", "qwen-plus-test", "test-call", "trend-moderation-v1"),
                now));
        assertTrue(trendRepository.finalizeHuman(
                "xiaohongshu:reference-test", "APPROVED", "test-admin", now, "测试夹具通过"));
        String body = """
                {"occasion":"通勤","city":"长沙","trendId":"xiaohongshu:reference-test"}
                """;
        mockMvc.perform(post("/api/v1/recommendations").with(user(userId)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        var captor = ArgumentCaptor.forClass(LlmRecommendationContext.class);
        verify(llmRecommendationClient).recommend(captor.capture());
        assertEquals("极简通勤", captor.getValue().trendReference().title());
        assertEquals(2, captor.getValue().wardrobe().size());
        trendRepository.hide("xiaohongshu:reference-test", true);
        mockMvc.perform(post("/api/v1/recommendations").with(user(userId)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/recommendations").with(user(userId)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("reference-test", "unknown"))).andExpect(status().isNotFound());
    }

    @Test
    void generatesSavesAndProtectsRecommendationByUser() throws Exception {
        String userId = "recommendation-user";
        long topId = createItem(userId, "米白衬衫", "上装", "暖白");
        createItem(userId, "黑色直筒裤", "下装", "石墨灰");
        createItem(userId, "低跟皮鞋", "鞋履", "黑色");

        var generated = mockMvc.perform(post("/api/v1/recommendations")
                        .with(user(userId))
                        .with(csrf())
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
                        .with(user(userId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(true));

        mockMvc.perform(post("/api/v1/me/recommendations/" + recommendationId + "/feedback")
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""" 
                                {"rating":5,"feedbackType":"useful","comment":"适合通勤"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback.rating").value(5))
                .andExpect(jsonPath("$.data.feedback.comment").value("适合通勤"));

        mockMvc.perform(get("/api/v1/me/recommendations")
                        .with(user(userId))
                        .header("X-User-Id", "another-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].saved").value(true))
                .andExpect(jsonPath("$.data.content[0].feedback.rating").value(5));

        mockMvc.perform(get("/api/v1/recommendations/" + recommendationId)
                        .with(user("another-user"))
                        .header("X-User-Id", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(delete("/api/v1/me/wardrobe/" + topId)
                        .with(user(userId))
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/recommendations/" + recommendationId).with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].name").value("米白衬衫"));
    }

    @Test
    void visualGenerationUsesOnlyCurrentUsersConfirmedRecommendationItems() throws Exception {
        String userId = "visual-recommendation-user";
        long topId = createItem(userId, "雾蓝衬衫", "上装", "雾蓝");
        long bottomId = createItem(userId, "深蓝长裤", "下装", "深蓝");
        mockMvc.perform(post("/api/v1/me/style-profile/refresh")
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"小夏\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk());

        var generated = mockMvc.perform(post("/api/v1/recommendations")
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occasion\":\"通勤\",\"city\":\"杭州\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long recommendationId = readData(generated).path("id").asLong();
        given(bailianImageGenerationClient.generate(any(), anyString(), anyString(), any(), any()))
                .willReturn(new OutfitImageGenerationResult(
                        "SUCCEEDED", "https://result.test/daily-look.png", "wan2.6-image", "visual-1", null));

        mockMvc.perform(post("/api/v1/me/recommendations/" + recommendationId + "/visual")
                        .with(user(userId)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://result.test/daily-look.png"))
                .andExpect(jsonPath("$.data.modelGender").value("FEMALE"))
                .andExpect(jsonPath("$.data.itemCount").value(2));

        ArgumentCaptor<List> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bailianImageGenerationClient).generate(
                eq("FEMALE"), eq("通勤"), eq("杭州"), eq(26.0), itemsCaptor.capture());
        List<?> items = itemsCaptor.getValue();
        assertEquals(2, items.size());
        assertEquals(List.of(topId, bottomId), items.stream()
                .map(item -> ((com.fashion.recommendation.wardrobe.WardrobeItem) item).id()).toList());
    }

    @Test
    void visualGenerationRefusesToInferGender() throws Exception {
        String userId = "visual-gender-required-user";
        createItem(userId, "雾蓝衬衫", "上装", "雾蓝");
        createItem(userId, "深蓝长裤", "下装", "深蓝");
        var generated = mockMvc.perform(post("/api/v1/recommendations")
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occasion\":\"通勤\",\"city\":\"杭州\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long recommendationId = readData(generated).path("id").asLong();

        mockMvc.perform(post("/api/v1/me/recommendations/" + recommendationId + "/visual")
                        .with(user(userId)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.message").value("请先在个人档案中选择每日模特性别"));
        verifyNoInteractions(bailianImageGenerationClient);
    }

    @Test
    void paginatesRecommendationHistoryAndRejectsUnboundedPageSizes() throws Exception {
        String userId = "recommendation-page-user";
        createItem(userId, "白色T恤", "上装", "白色");
        createItem(userId, "黑色长裤", "下装", "黑色");
        for (int index = 0; index < 3; index++) {
            mockMvc.perform(post("/api/v1/recommendations")
                            .with(user(userId))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"occasion":"通勤","city":"长沙"}
                                    """))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/me/recommendations?page=0&size=2").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        mockMvc.perform(get("/api/v1/me/recommendations?page=1&size=2").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        mockMvc.perform(get("/api/v1/me/recommendations?page=0&size=51").with(user(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void boundsHistoryOffsetAndRejectsOverflowingPages() throws Exception {
        String userId = "history-offset-boundary-user";
        mockMvc.perform(get("/api/v1/me/recommendations?page=20000&size=50").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        mockMvc.perform(get("/api/v1/me/recommendations?page=20001&size=50").with(user(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/v1/me/recommendations?page=2147483647&size=1").with(user(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/v1/me/recommendations?page=2147483647&size=50").with(user(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/v1/me/recommendations?page=-1&size=20").with(user(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void refusesRecommendationWhenWardrobeCannotFormAnOutfit() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations")
                        .with(user("empty-wardrobe-user"))
                        .with(csrf())
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
                        .with(user(userId))
                        .with(csrf())
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
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"通勤","city":"长沙"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("development-rule-v1"))
                .andReturn();
        long firstId = readData(first).path("id").asLong();

        mockMvc.perform(post("/api/v1/me/recommendations/" + firstId + "/feedback")
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":5,"feedbackType":"useful"}
                                """))
                .andExpect(status().isOk());

        long newerTopId = createItem(userId, "灰色卫衣", "上装", "石墨灰");

        mockMvc.perform(post("/api/v1/recommendations")
                        .with(user(userId))
                        .with(csrf())
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

        mockMvc.perform(delete("/api/v1/me/wardrobe/" + itemId)
                        .with(user(userId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/me/wardrobe").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void commitsDatabaseDeletionAndQueuesImageCleanupWithoutCallingStorageInTheTransaction() throws Exception {
        String userId = "delete-image-cleanup-user";
        given(imageStorage.store(anyString(), any())).willReturn(new StoredImage("wardrobe/delete-image-cleanup-user/a.png", "image/png"));
        given(imageStorage.read(anyString())).willReturn(new com.fashion.recommendation.storage.StoredImageData(
                new byte[] {1}, "image/png"));

        var upload = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                        "/api/v1/me/wardrobe/upload")
                        .file(new MockMultipartFile("image", "shirt.png", "image/png", new byte[] {1, 2, 3}))
                        .param("name", "待删衬衫")
                        .param("category", "上装")
                        .param("color", "白色")
                        .with(user(userId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        long itemId = readData(upload).path("id").asLong();
        mockMvc.perform(delete("/api/v1/me/wardrobe/" + itemId)
                        .with(user(userId))
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/wardrobe").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM image_cleanup_tasks WHERE object_key = ?",
                Integer.class, "wardrobe/delete-image-cleanup-user/a.png"));
        org.mockito.Mockito.verify(imageStorage, org.mockito.Mockito.never()).delete(anyString());
    }

    @Test
    void persistsStylePreferencesAndIgnoresClientTemperature() throws Exception {
        String userId = "profile-user";
        mockMvc.perform(post("/api/v1/me/style-profile/refresh")
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"小林","gender":"FEMALE","stylePreferences":["复古","通勤"],"colorPreferences":["酒红","深蓝"],"occasions":["约会","通勤"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("小林"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.colorPreferences[0]").value("酒红"));

        mockMvc.perform(get("/api/v1/me/style-profile").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stylePreferences[0]").value("复古"))
                .andExpect(jsonPath("$.data.occasions[0]").value("约会"));

        createItem(userId, "针织衫", "上装", "酒红");
        createItem(userId, "半身裙", "下装", "深蓝");
        mockMvc.perform(post("/api/v1/recommendations")
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"约会","city":"长沙","temperatureC":-10.0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temperatureC").value(26.0))
                .andExpect(jsonPath("$.data.reason").value(org.hamcrest.Matchers.containsString("复古、通勤")));
    }

    @Test
    void rejectsOversizedFeedbackAndStyleProfileFieldsBeforePersistence() throws Exception {
        String oversizedName = "名".repeat(81);
        var oversizedProfile = objectMapper.createObjectNode().put("displayName", oversizedName);
        oversizedProfile.putArray("stylePreferences").add("极简");
        mockMvc.perform(post("/api/v1/me/style-profile/refresh")
                        .with(user("validation-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedProfile.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/v1/me/style-profile/refresh")
                        .with(user("validation-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"小林\",\"gender\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/v1/me/recommendations/999999/feedback")
                        .with(user("validation-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("rating", 5)
                                .put("feedbackType", "x".repeat(81))
                                .put("comment", "x".repeat(501))
                                .toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void usesLlmResultAndPassesCompleteRecommendationContext() throws Exception {
        String userId = "llm-success-user";
        long topId = createItem(userId, "米白衬衫", "上装", "暖白");
        long bottomId = createItem(userId, "深蓝直筒裤", "下装", "深蓝");
        createItem(userId, "低跟皮鞋", "鞋履", "黑色");
        mockMvc.perform(post("/api/v1/me/style-profile/refresh")
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"小夏","stylePreferences":["复古"],"colorPreferences":["深蓝"],"occasions":["约会"]}
                                """))
                .andExpect(status().isOk());

        given(llmRecommendationClient.recommend(any(LlmRecommendationContext.class))).willReturn(Optional.of(
                new LlmRecommendationResult("深蓝复古穿搭", "色彩呼应风格档案与当前天气。", List.of(topId, bottomId))));

        mockMvc.perform(post("/api/v1/recommendations")
                        .with(user(userId))
                        .with(csrf())
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
    void persistsAndReturnsLlmAuditMetadata() throws Exception {
        String userId = "llm-audit-user";
        long topId = createItem(userId, "米白衬衫", "上装", "暖白");
        long bottomId = createItem(userId, "深蓝直筒裤", "下装", "深蓝");
        given(llmRecommendationClient.recommend(any(LlmRecommendationContext.class))).willReturn(Optional.of(
                new LlmRecommendationResult("深蓝复古穿搭", "色彩呼应风格档案与当前天气。", List.of(topId, bottomId),
                        "chatcmpl-abc123", "qwen-plus-test", "recommendation-v1", 617, 226, 843)));

        var result = mockMvc.perform(post("/api/v1/recommendations")
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"约会","city":"长沙"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("llm"))
                .andExpect(jsonPath("$.data.generationAudit.engine").value("llm"))
                .andExpect(jsonPath("$.data.generationAudit.fallbackReason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.generationAudit.modelName").value("qwen-plus-test"))
                .andExpect(jsonPath("$.data.generationAudit.promptVersion").value("recommendation-v1"))
                .andExpect(jsonPath("$.data.generationAudit.providerCallId").value("chatcmpl-abc123"))
                .andExpect(jsonPath("$.data.generationAudit.promptTokens").value(617))
                .andExpect(jsonPath("$.data.generationAudit.completionTokens").value(226))
                .andExpect(jsonPath("$.data.generationAudit.totalTokens").value(843))
                .andExpect(jsonPath("$.data.generationAudit.generationLatencyMs").isNumber())
                .andReturn();
        long recommendationId = readData(result).path("id").asLong();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT model_name, prompt_version, provider_call_id, prompt_tokens, completion_tokens, total_tokens, "
                        + "generation_latency_ms, fallback_reason FROM recommendations WHERE id = ?",
                recommendationId);
        assertEquals("qwen-plus-test", row.get("model_name"));
        assertEquals("recommendation-v1", row.get("prompt_version"));
        assertEquals("chatcmpl-abc123", row.get("provider_call_id"));
        assertEquals(617, ((Number) row.get("prompt_tokens")).intValue());
        assertEquals(226, ((Number) row.get("completion_tokens")).intValue());
        assertEquals(843, ((Number) row.get("total_tokens")).intValue());
        assertTrue(((Number) row.get("generation_latency_ms")).longValue() >= 0);
        assertNull(row.get("fallback_reason"));
    }

    @Test
    void recordsFallbackReasonWhenLlmIsNotConfigured() throws Exception {
        String userId = "llm-no-key-user";
        createItem(userId, "针织衫", "上装", "酒红");
        createItem(userId, "半身裙", "下装", "深蓝");

        mockMvc.perform(post("/api/v1/recommendations")
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"约会","city":"长沙"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("development-rule-v1"))
                .andExpect(jsonPath("$.data.generationAudit.fallbackReason").value("missing-api-key"))
                .andExpect(jsonPath("$.data.generationAudit.modelName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.generationAudit.providerCallId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.generationAudit.promptTokens").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void recordsFallbackReasonWhenLlmThrows() throws Exception {
        String userId = "llm-throw-user";
        createItem(userId, "针织衫", "上装", "酒红");
        createItem(userId, "半身裙", "下装", "深蓝");
        given(llmRecommendationClient.recommend(any(LlmRecommendationContext.class)))
                .willThrow(new ResourceAccessException("LLM timeout", new SocketTimeoutException("read timed out")));

        mockMvc.perform(post("/api/v1/recommendations")
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"约会","city":"长沙"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("development-rule-v1"))
                .andExpect(jsonPath("$.data.generationAudit.fallbackReason").value("request-failed"));
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
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"通勤","city":"长沙"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("development-rule-v1"))
                .andExpect(jsonPath("$.data.summary").value(org.hamcrest.Matchers.not("不应接受的推荐")))
                .andExpect(jsonPath("$.data.generationAudit.fallbackReason").value("foreign-item-ids"))
                .andReturn();

        JsonNode items = readData(result).path("items");
        assertEquals(3, items.size());
        List<Long> currentUserItemIds = List.of(topId, bottomId, shoeId);
        items.forEach(item -> assertTrue(currentUserItemIds.contains(item.path("id").asLong())));
        assertFalse(items.findValuesAsText("id").contains(Long.toString(foreignItemId)));
    }

    @Test
    void rejectsLlmResultWhenAllSelectedItemsShareOneCategory() throws Exception {
        String userId = "llm-category-validation-user";
        long firstTopId = createItem(userId, "白色T恤", "上装", "白色");
        long secondTopId = createItem(userId, "灰色卫衣", "上装", "灰色");
        createItem(userId, "黑色长裤", "下装", "黑色");
        given(llmRecommendationClient.recommend(any(LlmRecommendationContext.class))).willReturn(Optional.of(
                new LlmRecommendationResult("不完整的搭配", "只包含两件上装。", List.of(firstTopId, secondTopId))));

        mockMvc.perform(post("/api/v1/recommendations")
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"通勤","city":"长沙"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("development-rule-v1"))
                .andExpect(jsonPath("$.data.summary").value(org.hamcrest.Matchers.not("不完整的搭配")))
                .andExpect(jsonPath("$.data.generationAudit.fallbackReason").value("same-category"))
                .andExpect(jsonPath("$.data.items.length()").value(3));
    }

    @Test
    void fallsBackToRuleEngineWhenLlmTimesOut() throws Exception {
        String userId = "llm-exception-user";
        createItem(userId, "针织衫", "上装", "酒红");
        createItem(userId, "半身裙", "下装", "深蓝");
        given(llmRecommendationClient.recommend(any(LlmRecommendationContext.class)))
                .willThrow(new ResourceAccessException("LLM timeout", new SocketTimeoutException("read timed out")));

        mockMvc.perform(post("/api/v1/recommendations")
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"约会","city":"长沙"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("development-rule-v1"))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void storesUploadedImageWithoutAiRecognitionByDefaultAndAllowsManualCorrection() throws Exception {
        String userId = "image-user";
        given(imageStorage.store(anyString(), any())).willReturn(new StoredImage("wardrobe/image-user/a.png", "image/png"));

        var upload = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                        "/api/v1/me/wardrobe/upload")
                        .file(new MockMultipartFile("image", "shirt.png", "image/png", new byte[] {1, 2, 3}))
                        .with(user(userId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recognitionStatus").value("NEEDS_MANUAL_REVIEW"))
                .andExpect(jsonPath("$.data.category").value("待识别"))
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.endsWith("/image")))
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("userId"))))
                .andReturn();
        long itemId = readData(upload).path("id").asLong();

        mockMvc.perform(put("/api/v1/me/wardrobe/" + itemId)
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"米白衬衫","category":"上装","color":"暖白","style":"极简"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recognitionStatus").value("MANUAL_CORRECTED"))
                .andExpect(jsonPath("$.data.name").value("米白衬衫"));

        mockMvc.perform(get("/api/v1/me/wardrobe").with(user("another-image-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        verify(imageStorage).store(eq(userId), any());
        verifyNoInteractions(garmentRecognitionService);
    }

    @Test
    void invokesAiRecognitionOnlyWhenExplicitlyAllowed() throws Exception {
        String userId = "ai-image-user";
        given(imageStorage.store(anyString(), any())).willReturn(
                new StoredImage("wardrobe/ai-image-user/a.png", "image/png"));
        given(garmentRecognitionService.recognize(any())).willReturn(Optional.of(
                new GarmentRecognitionResult("雾蓝衬衫", "上装", "雾蓝", "极简")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                        "/api/v1/me/wardrobe/upload")
                        .file(new MockMultipartFile("image", "shirt.png", "image/png", new byte[] {1, 2, 3}))
                        .param("allowAiRecognition", "true")
                        .with(user(userId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recognitionStatus").value("RECOGNIZED"))
                .andExpect(jsonPath("$.data.name").value("雾蓝衬衫"))
                .andExpect(jsonPath("$.data.category").value("上装"));

        verify(garmentRecognitionService).recognize(any());
    }

    @Test
    void rejectsUnsupportedImageBeforeStorage() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                        "/api/v1/me/wardrobe/upload")
                        .file(new MockMultipartFile("image", "script.svg", "image/svg+xml", new byte[] {1}))
                        .with(user("invalid-image-user"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(imageStorage, garmentRecognitionService);
    }

    private long createItem(String userId, String name, String category, String color) throws Exception {
        var result = mockMvc.perform(post("/api/v1/me/wardrobe")
                        .with(user(userId))
                        .with(csrf())
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
