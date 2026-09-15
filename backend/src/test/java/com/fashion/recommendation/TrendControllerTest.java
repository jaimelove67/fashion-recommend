package com.fashion.recommendation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TrendControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearTrendFixtures() {
        jdbcTemplate.update("DELETE FROM trend_contents");
    }

    @Test
    void rejectsInvalidWindowAndProtectsAdminRefresh() throws Exception {
        mockMvc.perform(get("/api/v1/trends?period=year")).andExpect(status().isBadRequest());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/trends/refresh"))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsAnHonestEmptyFeedWhenNoSourceIsConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/trends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.primarySource").value("multi-source"))
                .andExpect(jsonPath("$.data.demoMode").value(false))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }
}
