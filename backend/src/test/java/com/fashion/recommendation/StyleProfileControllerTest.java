package com.fashion.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.recommendation.LlmRecommendationClient;
import com.fashion.recommendation.recommendation.LlmRecommendationContext;
import com.fashion.recommendation.recommendation.BailianImageGenerationClient;
import com.fashion.recommendation.recommendation.OutfitImageGenerationResult;
import com.fashion.recommendation.storage.ImageStorage;
import com.fashion.recommendation.storage.StoredImage;
import com.fashion.recommendation.storage.StoredImageData;
import com.fashion.recommendation.style.PersonalStyleAnalysis;
import com.fashion.recommendation.style.PersonalStyleAnalysisClient;
import com.fashion.recommendation.style.PersonalStyleAnalysisResult;
import com.fashion.recommendation.style.PersonalStyleProfileService;
import com.fashion.recommendation.style.StyleProfileRefreshRequest;
import com.fashion.recommendation.weather.WeatherService;
import com.fashion.recommendation.weather.WeatherSnapshot;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StyleProfileControllerTest {
    private static final byte[] PHOTO = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jP1sAAAAASUVORK5CYII=");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PersonalStyleProfileService profiles;
    @MockBean PersonalStyleAnalysisClient analysisClient;
    @MockBean ImageStorage storage;
    @MockBean WeatherService weather;
    @MockBean LlmRecommendationClient recommendations;
    @MockBean BailianImageGenerationClient images;

    @BeforeEach
    void providers() {
        when(storage.store(anyString(), any())).thenAnswer(call -> new StoredImage("portraits/" + call.getArgument(0) + "/photo.png", null));
        when(storage.read(anyString())).thenReturn(new StoredImageData(PHOTO, "image/png"));
        when(analysisClient.analyze(any(), any())).thenReturn(new PersonalStyleAnalysisResult(analysis("椭圆偏长", "圆领"), "vision-tested"));
        when(recommendations.recommend(any())).thenReturn(Optional.empty());
        when(weather.current(anyString())).thenReturn(new WeatherSnapshot("长沙", 24, 24.0, 0.0, 1, 5.0, Instant.now(), "test"));
    }

    @Test
    void persistsMeasurementsPhotoAnalysisAndManualCorrectionIntoTheNextRecommendation() throws Exception {
        String actor = "profile-complete-user";
        basic(actor, 175, 65);
        upload(actor).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/me/style-profile/photo?")))
                .andExpect(jsonPath("$.data.photoObjectKey").doesNotExist())
                .andExpect(jsonPath("$.data.revision").doesNotExist());
        postJson(actor, "/analyze", "{\"allowAiAnalysis\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysis.faceShape").value("椭圆偏长"))
                .andExpect(jsonPath("$.data.analysisSource").value("MODEL"))
                .andExpect(jsonPath("$.data.analysisModelName").value("vision-tested"));
        mvc.perform(get("/api/v1/me/style-profile").with(user(actor)))
                .andExpect(jsonPath("$.data.heightCm").value(175.0))
                .andExpect(jsonPath("$.data.analysis.fitSuggestions[0]").value("圆领"));
        PersonalStyleAnalysis corrected = analysis("方脸", "V领");
        postJson(actor, "/analysis", mapper.writeValueAsString(corrected)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisSource").value("MANUAL"))
                .andExpect(jsonPath("$.data.analysisModelName").value(org.hamcrest.Matchers.nullValue()));
        item(actor, "V领米白上衣", "上装", "米白");
        item(actor, "直筒裤", "下装", "黑色");
        generate(actor).andExpect(status().isOk());
        ArgumentCaptor<LlmRecommendationContext> context = ArgumentCaptor.forClass(LlmRecommendationContext.class);
        verify(recommendations).recommend(context.capture());
        assertEquals("方脸", context.getValue().styleProfile().analysis().faceShape());
        assertEquals(175.0, context.getValue().styleProfile().heightCm());
        assertEquals("MANUAL", context.getValue().styleProfile().analysisSource());
        assertEquals("方脸", profiles.current(actor).analysis().faceShape());
    }

    @Test
    void ruleFallbackSelectsClothesMatchingSavedColorAndFitAdvice() throws Exception {
        String actor = "profile-rule-user";
        long preferred = item(actor, "圆领米白上衣", "上装", "米白");
        long other = item(actor, "蓝色衬衫", "上装", "蓝色");
        item(actor, "直筒裤", "下装", "黑色");
        postJson(actor, "/analysis", mapper.writeValueAsString(analysis("椭圆偏长", "圆领"))).andExpect(status().isOk());
        JsonNode result = data(generate(actor).andExpect(status().isOk()));
        assertEquals("development-rule-v1", result.path("engine").asText());
        List<Long> ids = mapper.convertValue(result.path("items").findValues("id"), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        assertEquals(preferred, result.path("items").get(0).path("id").asLong());
        assertTrue(ids.contains(preferred));
        assertTrue(result.path("reason").asText().contains("个人档案"));
        assertNotEquals(other, result.path("items").get(0).path("id").asLong());
    }

    @Test
    void changedMeasurementsMarkOldAnalysisStaleAndStopItsInfluenceOnRuleSelection() throws Exception {
        String actor = "profile-stale-user";
        basic(actor, 175, 65);
        item(actor, "圆领米白上衣", "上装", "米白");
        long plain = item(actor, "蓝色衬衫", "上装", "蓝色");
        item(actor, "长裤", "下装", "黑色");
        postJson(actor, "/analysis", mapper.writeValueAsString(analysis("椭圆偏长", "圆领"))).andExpect(status().isOk());
        basic(actor, 180, 65).andExpect(jsonPath("$.data.stale").value(true));
        JsonNode result = data(generate(actor).andExpect(status().isOk()));
        assertEquals(plain, result.path("items").get(0).path("id").asLong());
        assertTrue(result.path("reason").asText().contains("旧分析暂未用于"));
    }

    @Test
    void doesNotCallVisionWithoutConsentOrRequiredInputs() throws Exception {
        String actor = "profile-consent-user";
        postJson(actor, "/analyze", "{}").andExpect(status().isBadRequest());
        postJson(actor, "/analyze", "{\"allowAiAnalysis\":false}").andExpect(status().isBadRequest());
        postJson(actor, "/analyze", "{\"allowAiAnalysis\":true}").andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(analysisClient);
    }

    @Test
    void failedAnalysisPreservesSavedInputsAndDoesNotReportSuccess() throws Exception {
        String actor = "profile-failure-user";
        basic(actor, 175, 65);
        upload(actor).andExpect(status().isOk());
        when(analysisClient.analyze(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "视觉模型未启用"));
        postJson(actor, "/analyze", "{\"allowAiAnalysis\":true}").andExpect(status().isServiceUnavailable());
        assertNull(profiles.current(actor).analysis());
        assertEquals(175.0, profiles.current(actor).heightCm());
        assertNotNull(profiles.current(actor).photoObjectKey());
        assertNull(profiles.current(actor).analysisSource());
    }

    @Test
    void rejectsLateAnalysisAfterNewMeasurementsOrManualCorrections() throws Exception {
        String actor = "profile-race-user";
        basic(actor, 175, 65);
        upload(actor).andExpect(status().isOk());
        when(analysisClient.analyze(any(), any())).thenAnswer(call -> {
            profiles.correctAnalysis(actor, analysis("人工确认的脸型", "V领"));
            return new PersonalStyleAnalysisResult(analysis("过时模型脸型", "圆领"), "vision-tested");
        });
        postJson(actor, "/analyze", "{\"allowAiAnalysis\":true}").andExpect(status().isConflict());
        assertEquals("人工确认的脸型", profiles.current(actor).analysis().faceShape());
        assertEquals("MANUAL", profiles.current(actor).analysisSource());
    }

    @Test
    void replacingPhotoSchedulesOldObjectCleanupAndInvalidatesItsAnalysis() throws Exception {
        String actor = "profile-replace-user";
        basic(actor, 175, 65);
        upload(actor).andExpect(status().isOk());
        postJson(actor, "/analyze", "{\"allowAiAnalysis\":true}").andExpect(status().isOk());
        postJson(actor, "/outfit-model", "{\"usePersonalPhotoForOutfit\":true}").andExpect(status().isOk());
        when(storage.store(eq(actor), any())).thenReturn(new StoredImage("portraits/" + actor + "/new.png", null));
        upload(actor).andExpect(status().isOk()).andExpect(jsonPath("$.data.stale").value(true))
                .andExpect(jsonPath("$.data.usePersonalPhotoForOutfit").value(false));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM image_cleanup_tasks WHERE object_key = ?", Integer.class,
                "portraits/" + actor + "/photo.png"));
        verify(storage, never()).delete("portraits/" + actor + "/photo.png");
    }

    @Test
    void privatePhotoRequiresSessionAndAlwaysUsesTheAuthenticatedOwner() throws Exception {
        String actor = "profile-private-user";
        upload(actor).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/style-profile/photo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/style-profile/photo?userId=" + actor).with(user("profile-other-user")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/style-profile/photo").with(user(actor)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(PHOTO));
        verify(storage).read("portraits/" + actor + "/photo.png");
        mvc.perform(post("/api/v1/me/style-profile/analysis").with(user(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(analysis("方脸", "V领")))).andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidMeasurementsForgedImagesAndOversizedAnalysis() throws Exception {
        String actor = "profile-validation-user";
        postJson(actor, "/refresh", "{\"displayName\":\"用户\",\"heightCm\":300}").andExpect(status().isBadRequest());
        postJson(actor, "/analysis", "{\"faceShape\":\"" + "长".repeat(121) + "\"}").andExpect(status().isBadRequest());
        postJson(actor, "/analysis", "{}").andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/me/style-profile/photo")
                .file(new MockMultipartFile("photo", "portrait.png", "image/png", "not-an-image".getBytes()))
                .with(user(actor)).with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(storage, analysisClient);
    }

    @Test
    void modelChoiceDefaultsToPresetRequiresAnOwnedPhotoAndSurvivesAnalysisAndBasicEdits() throws Exception {
        String actor = "outfit-model-preference-user";
        basic(actor, 175, 65).andExpect(jsonPath("$.data.usePersonalPhotoForOutfit").value(false));
        postJson(actor, "/outfit-model", "{}").andExpect(status().isBadRequest());
        postJson(actor, "/outfit-model", "{\"usePersonalPhotoForOutfit\":true}").andExpect(status().isUnprocessableEntity());
        upload(actor).andExpect(status().isOk());
        String photoUrl = profiles.current(actor).photoUrl();
        postJson(actor, "/outfit-model", "{\"usePersonalPhotoForOutfit\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usePersonalPhotoForOutfit").value(true))
                .andExpect(jsonPath("$.data.photoObjectKey").doesNotExist());
        basic(actor, 176, 65).andExpect(jsonPath("$.data.usePersonalPhotoForOutfit").value(true))
                .andExpect(jsonPath("$.data.photoUrl").value(photoUrl));
        postJson(actor, "/analyze", "{\"allowAiAnalysis\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usePersonalPhotoForOutfit").value(true))
                .andExpect(jsonPath("$.data.photoUrl").value(photoUrl));
        postJson(actor, "/analysis", mapper.writeValueAsString(analysis("方脸", "V领"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usePersonalPhotoForOutfit").value(true));
        postJson(actor, "/outfit-model", "{\"usePersonalPhotoForOutfit\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usePersonalPhotoForOutfit").value(false))
                .andExpect(jsonPath("$.data.analysis.faceShape").value("方脸"))
                .andExpect(jsonPath("$.data.stale").value(false));
        assertFalse(profiles.current(actor).usePersonalPhotoForOutfit());
    }

    @Test
    void outfitGenerationUsesPresetUnlessPersonalPhotoIsExplicitlySelected() throws Exception {
        String actor = "outfit-visual-preference-user";
        basic(actor, 175, 65);
        upload(actor).andExpect(status().isOk());
        item(actor, "衬衫", "上装", "米白");
        item(actor, "长裤", "下装", "深蓝");
        long id = data(generate(actor).andExpect(status().isOk())).path("id").asLong();
        var result = new OutfitImageGenerationResult("SUCCEEDED", "https://images.test/look.png", "wan2.6-image", "request", null);
        when(images.generate(anyString(), anyString(), anyString(), anyDouble(), anyList())).thenReturn(result);
        when(images.generate(anyString(), anyString(), anyString(), anyDouble(), anyList(), any(StoredImageData.class))).thenReturn(result);
        clearInvocations(storage);
        mvc.perform(post("/api/v1/me/recommendations/" + id + "/visual").with(user(actor)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.modelSource").value("DEFAULT"));
        verify(images).generate(eq("MALE"), anyString(), eq("长沙"), anyDouble(), anyList());
        verify(storage, never()).read(anyString());
        postJson(actor, "/outfit-model", "{\"usePersonalPhotoForOutfit\":true}").andExpect(status().isOk());
        mvc.perform(post("/api/v1/me/recommendations/" + id + "/visual").with(user(actor)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.modelSource").value("PERSONAL"));
        var photo = ArgumentCaptor.forClass(StoredImageData.class);
        verify(images).generate(eq("MALE"), anyString(), eq("长沙"), anyDouble(), anyList(), photo.capture());
        assertArrayEquals(PHOTO, photo.getValue().content());
        verify(storage).read("portraits/" + actor + "/photo.png");
        clearInvocations(images, storage);
        mvc.perform(post("/api/v1/me/recommendations/" + id + "/visual").with(user("outfit-other-user")).with(csrf()))
                .andExpect(status().isNotFound());
        verifyNoInteractions(images, storage);
    }

    @Test
    void modelPreferenceRequiresAuthenticationCsrfAndCannotSelectAnotherUsersPhoto() throws Exception {
        String actor = "outfit-model-private-user";
        upload(actor).andExpect(status().isOk());
        mvc.perform(post("/api/v1/me/style-profile/outfit-model").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usePersonalPhotoForOutfit\":true}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/me/style-profile/outfit-model").with(user(actor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usePersonalPhotoForOutfit\":true}")).andExpect(status().isForbidden());
        postJson("outfit-model-photo-other-user", "/outfit-model?userId=" + actor, "{\"usePersonalPhotoForOutfit\":true}")
                .andExpect(status().isUnprocessableEntity());
        assertFalse(profiles.current(actor).usePersonalPhotoForOutfit());
    }

    @Test
    void outfitPhotoReadRejectsAnOldPermissionSnapshotAfterPhotoReplacement() throws Exception {
        String actor = "outfit-model-snapshot-user";
        upload(actor).andExpect(status().isOk());
        postJson(actor, "/outfit-model", "{\"usePersonalPhotoForOutfit\":true}").andExpect(status().isOk());
        String oldPhotoKey = profiles.current(actor).photoObjectKey();
        when(storage.store(eq(actor), any())).thenReturn(new StoredImage("portraits/" + actor + "/replacement.png", null));
        upload(actor).andExpect(status().isOk());
        postJson(actor, "/outfit-model", "{\"usePersonalPhotoForOutfit\":true}").andExpect(status().isOk());
        clearInvocations(storage);
        var conflict = assertThrows(ResponseStatusException.class, () -> profiles.readOutfitPhoto(actor, oldPhotoKey));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        verify(storage, never()).read(anyString());
        profiles.readOutfitPhoto(actor, profiles.current(actor).photoObjectKey());
        verify(storage).read("portraits/" + actor + "/replacement.png");
    }

    @Test
    void newProfilesStartWithUnknownPreferencesAndLegacyValuesRequireReview() throws Exception {
        String actor = "preference-new-user";
        mvc.perform(get("/api/v1/me/style-profile").with(user(actor))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stylePreferences").isEmpty())
                .andExpect(jsonPath("$.data.colorPreferences").isEmpty())
                .andExpect(jsonPath("$.data.occasions").isEmpty())
                .andExpect(jsonPath("$.data.preferencesConfirmed").value(false));
        profiles.refresh("preference-legacy-user", new StyleProfileRefreshRequest("旧称呼", null,
                List.of("极简", "通勤"), List.of("低饱和"), List.of("通勤"), null, null));
        mvc.perform(get("/api/v1/me/style-profile").with(user("preference-legacy-user")))
                .andExpect(jsonPath("$.data.stylePreferences[0]").value("极简"))
                .andExpect(jsonPath("$.data.preferencesConfirmed").value(false));
    }

    @Test
    void savesOnlyConfirmedPartialPreferencesAndKeepsPhotoAndModelSettings() throws Exception {
        String actor = "preference-partial-user";
        basic(actor, 175, 65);
        upload(actor).andExpect(status().isOk());
        postJson(actor, "/outfit-model", "{\"usePersonalPhotoForOutfit\":true}").andExpect(status().isOk());
        postJson(actor, "/analyze", "{\"allowAiAnalysis\":true}").andExpect(status().isOk());
        String photo = profiles.current(actor).photoUrl();
        postJson(actor, "/refresh", """
                {"displayName":"用户","gender":"MALE","heightCm":175,"weightKg":65,
                 "stylePreferences":[],"colorPreferences":["浅蓝"],"occasions":[],
                 "avoidPreferences":["紧身"],"confirmPreferences":true}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stylePreferences").isEmpty())
                .andExpect(jsonPath("$.data.colorPreferences[0]").value("浅蓝"))
                .andExpect(jsonPath("$.data.avoidPreferences[0]").value("紧身"))
                .andExpect(jsonPath("$.data.preferencesConfirmed").value(true))
                .andExpect(jsonPath("$.data.photoUrl").value(photo))
                .andExpect(jsonPath("$.data.usePersonalPhotoForOutfit").value(true))
                .andExpect(jsonPath("$.data.stale").value(true));
        postJson(actor, "/refresh", """
                {"displayName":"用户","gender":"MALE","heightCm":176,"weightKg":65,
                 "stylePreferences":[],"colorPreferences":["浅蓝"],"occasions":[]}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferencesConfirmed").value(true))
                .andExpect(jsonPath("$.data.avoidPreferences[0]").value("紧身"));
        mvc.perform(get("/api/v1/me/style-profile").with(user("preference-other-user")))
                .andExpect(jsonPath("$.data.colorPreferences").isEmpty());
    }

    @Test
    void rejectsOversizedAvoidConditionsWithoutChangingSavedPreferences() throws Exception {
        String actor = "preference-validation-user";
        postJson(actor, "/refresh", "{\"displayName\":\"用户\",\"avoidPreferences\":[\"" + "长".repeat(41)
                + "\"],\"confirmPreferences\":true}").andExpect(status().isBadRequest());
        assertTrue(profiles.current(actor).avoidPreferences().isEmpty());
        assertFalse(profiles.current(actor).preferencesConfirmed());
    }

    private ResultActions basic(String actor, double height, double weight) throws Exception {
        return postJson(actor, "/refresh", """
                {"displayName":"用户","gender":"MALE","stylePreferences":[],"colorPreferences":[],"occasions":[],
                 "heightCm":%s,"weightKg":%s}
                """.formatted(height, weight)).andExpect(status().isOk());
    }

    private ResultActions upload(String actor) throws Exception {
        return mvc.perform(multipart("/api/v1/me/style-profile/photo")
                .file(new MockMultipartFile("photo", "portrait.png", "image/png", PHOTO)).with(user(actor)).with(csrf()));
    }

    private ResultActions postJson(String actor, String suffix, String json) throws Exception {
        return mvc.perform(post("/api/v1/me/style-profile" + suffix).with(user(actor)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long item(String actor, String name, String category, String color) throws Exception {
        return data(mvc.perform(post("/api/v1/me/wardrobe").with(user(actor)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.createObjectNode().put("name", name).put("category", category).put("color", color).toString()))
                .andExpect(status().isOk())).path("id").asLong();
    }

    private ResultActions generate(String actor) throws Exception {
        return mvc.perform(post("/api/v1/recommendations").with(user(actor)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"city\":\"长沙\",\"occasion\":\"通勤\"}"));
    }

    private JsonNode data(ResultActions actions) throws Exception {
        return mapper.readTree(actions.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    private static PersonalStyleAnalysis analysis(String face, String fit) {
        return new PersonalStyleAnalysis(face, "清晰", "中等", "短发", "", List.of(fit), List.of("简洁"), List.of(),
                List.of("米白"), List.of(fit + "上衣"), "根据已确认特征参考领口、版型与配色。");
    }
}
