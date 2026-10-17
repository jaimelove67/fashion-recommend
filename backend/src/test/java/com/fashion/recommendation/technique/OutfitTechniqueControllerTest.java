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
    private final WeeklyTechniqueService.OperationalStatus state = new WeeklyTechniqueService.OperationalStatus(
            "disabled", false, null, null, Instant.parse("2026-10-16T02:00:00Z"), null, 0, 2);

    @BeforeEach
    void fixture() {
        when(service.currentFeed()).thenReturn(new WeeklyTechniqueService.TechniqueFeed(List.of(), null, "weekly", "disabled"));
        when(service.status()).thenReturn(state);
        when(service.refresh()).thenReturn(new WeeklyTechniqueService.RefreshResult(false, "disabled", state));
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
