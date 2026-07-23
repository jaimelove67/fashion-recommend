package com.fashion.recommendation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TrendControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsTheExplicitDevelopmentTrendFeedWhenNoSourceIsConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/trends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.primarySource").value("douyin-development-sample"))
                .andExpect(jsonPath("$.data.demoMode").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(3));
    }
}
