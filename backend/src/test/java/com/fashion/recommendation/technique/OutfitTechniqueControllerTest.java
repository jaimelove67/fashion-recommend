package com.fashion.recommendation.technique;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.techniques.enabled=false")
@AutoConfigureMockMvc
class OutfitTechniqueControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @MockBean private WeeklyTechniqueService service;
    @MockBean private TechniqueComparisonVisualService visuals;
    @MockBean private TechniqueComparisonVisualRepository images;
    private final WeeklyTechniqueService.OperationalStatus state = new WeeklyTechniqueService.OperationalStatus(
            "disabled", false, null, null, Instant.parse("2026-10-16T02:00:00Z"), null, 0, 2);

    @BeforeEach
    void fixture() {
        when(service.currentFeed()).thenReturn(new WeeklyTechniqueService.TechniqueFeed(List.of(), null, "weekly", "disabled"));
        when(service.status()).thenReturn(state);
        when(service.refresh()).thenReturn(new WeeklyTechniqueService.RefreshResult(false, "disabled", state));
        when(visuals.budget()).thenReturn(new TechniqueComparisonVisualRepository.Budget("2026-10", 3, 10));
        when(visuals.start()).thenReturn(true);
        when(images.image(anyString())).thenReturn(java.util.Optional.empty());
    }

    @Test
    void comparisonGenerationRequiresAdminAndCsrfAndPublicReadsDoNotGenerate() throws Exception {
        mvc.perform(post("/api/v1/admin/outfit-techniques/comparison-visuals")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/outfit-techniques/comparison-visuals").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/outfit-techniques/comparison-visuals").with(user("member").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        verify(visuals, never()).start();
        mvc.perform(get("/api/v1/admin/outfit-techniques/comparison-visuals/status").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.used").value(3)).andExpect(jsonPath("$.data.limit").value(10));
        mvc.perform(get("/api/v1/outfit-techniques")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/outfit-techniques/comparison-images/12345678-1234-1234-1234-123456789abc"))
                .andExpect(status().isNotFound());
        verify(visuals, never()).start();
        mvc.perform(post("/api/v1/admin/outfit-techniques/comparison-visuals").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.budget.month").value("2026-10"));
        verify(visuals, times(1)).start();
    }

    @Test
    void publicImageReadsOnlyTheControlledAssetAndSetsCacheAndNosniffHeaders() throws Exception {
        String id = "12345678-1234-1234-1234-123456789abc";
        when(images.image(id)).thenReturn(java.util.Optional.of(new com.fashion.recommendation.storage.StoredImageData(
                new byte[] {1, 2, 3}, "image/png")));
        mvc.perform(get("/api/v1/outfit-techniques/comparison-images/" + id)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));
        mvc.perform(get("/api/v1/outfit-techniques/comparison-images/private-object-key")).andExpect(status().isNotFound());
        verify(visuals, never()).start();
    }

    @Test
    void anonymousFeedIsReadOnlyAndHasNoGenerationTrigger() throws Exception {
        mvc.perform(get("/api/v1/outfit-techniques"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cadence").value("weekly"))
                .andExpect(jsonPath("$.data.items.length()").value(0)).andExpect(jsonPath("$.data.status").value("disabled"));
        verify(service).currentFeed();
        verify(service, never()).refresh();
    }

    @Test
    void refreshRequiresAdminAndTheActualCsrfToken() throws Exception {
        mvc.perform(get("/api/v1/admin/outfit-techniques/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/outfit-techniques/status").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/outfit-techniques/refresh").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/outfit-techniques/refresh").with(user("member").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        verify(service, never()).refresh();
        mvc.perform(get("/api/v1/admin/outfit-techniques/status").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.weeklyAttemptLimit").value(2));
        mvc.perform(post("/api/v1/admin/outfit-techniques/refresh").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.attempted").value(false))
                .andExpect(jsonPath("$.data.outcome").value("disabled"));
        verify(service, times(1)).refresh();
    }

    private RequestPostProcessor csrf() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var token = mapper.readTree(result.getResponse().getContentAsString()).path("data");
        return request -> {
            request.setCookies(result.getResponse().getCookies());
            request.addHeader(token.path("headerName").asText(), token.path("token").asText());
            return request;
        };
    }
}
