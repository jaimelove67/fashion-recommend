package com.fashion.recommendation.trend;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrendContentReviewControllerTest {
    @Autowired MockMvc mvc;
    @Autowired TrendRepository repository;
    @Autowired ObjectMapper mapper;

    @Test void onlyAdministratorsWithCsrfCanApproveActualReviewedVersions() throws Exception {
        var post = newPost("weibo:normal-half-body", "正常半身针织衫穿搭");
        repository.save("weibo", post);
        String fingerprint = repository.reviewCandidate(post.id()).orElseThrow().fingerprint();
        String body = mapper.writeValueAsString(Map.of("approved", true, "fingerprint", fingerprint,
                "note", "正常半身服装展示，已检查所有图片"));
        String endpoint = "/api/v1/admin/trends/" + post.id() + "/content-review";
        mvc.perform(get("/api/v1/admin/trends/review-queue")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/trends/review-queue").with(user("member"))).andExpect(status().isForbidden());
        mvc.perform(post(endpoint).with(user("member")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(endpoint).with(user("reviewer").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertTrue(repository.find(post.id()).isEmpty());
        mvc.perform(post(endpoint).with(user("reviewer").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertTrue(repository.find(post.id()).isPresent());
        mvc.perform(post(endpoint).with(user("reviewer").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body.replace(fingerprint, "0".repeat(64))))
                .andExpect(status().isConflict());
    }

    @Test void explicitBodyFocusedCopyCannotBeApprovedAndLimitsAreValidated() throws Exception {
        var post = newPost("weibo:body-focused", "OOTD 胸部特写");
        repository.save("weibo", post);
        String body = mapper.writeValueAsString(Map.of("approved", true,
                "fingerprint", repository.reviewCandidate(post.id()).orElseThrow().fingerprint(), "note", "不应通过"));
        mvc.perform(post("/api/v1/admin/trends/" + post.id() + "/content-review")
                        .with(user("reviewer").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/trends/review-queue?limit=1000").with(user("reviewer").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        assertTrue(repository.find(post.id()).isEmpty());
    }

    private TrendItem newPost(String id, String title) {
        Instant now = Instant.now();
        return new TrendItem(id, "weibo", title, List.of("日常穿搭"), 0, now.minusSeconds(60), now,
                "https://weibo.com/test", false, "https://images.example/outfit.jpg", null,
                new TrendEvidence("author", "image", List.of("https://images.example/outfit.jpg"),
                        null, null, null, null, "互动", null));
    }
}
