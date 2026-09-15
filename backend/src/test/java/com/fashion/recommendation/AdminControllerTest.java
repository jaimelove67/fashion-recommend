package com.fashion.recommendation;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminControllerTest {
    private static final String PASSWORD = "StrongPass123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void removeTrendFixtures() {
        jdbcTemplate.update("DELETE FROM trend_contents WHERE id LIKE 'weibo:admin-moderation-%'");
    }

    @Test
    void protectsAdminEndpointsFromAnonymousAndOrdinaryUsers() throws Exception {
        String admin = createUser("adm", true, "ROLE_ADMIN");
        String member = createUser("member", true, "ROLE_USER");

        mockMvc.perform(get("/api/v1/admin/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/v1/admin/overview")
                        .with(user(member).roles("USER"))
                        .header("X-User-Id", admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void returnsAggregatesAndSanitizedPaginatedAccountsToAdmin() throws Exception {
        String admin = createUser("adm", true, "ROLE_ADMIN");
        String member = createUser("member", true, "ROLE_USER");

        mockMvc.perform(get("/api/v1/admin/overview")
                        .with(user(admin).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.enabledUsers", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.disabledUsers").value(0))
                .andExpect(jsonPath("$.data.totalWardrobeItems").isNumber())
                .andExpect(jsonPath("$.data.totalRecommendations").isNumber());

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "0")
                        .param("size", "10")
                        .param("query", member)
                        .with(user(admin).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value(member))
                .andExpect(jsonPath("$.data.items[0].enabled").value(true))
                .andExpect(jsonPath("$.data.items[0].authorities[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());
    }

    @Test
    void exposesAdminAuthorityThroughLoginAndCurrentUser() throws Exception {
        String admin = createUser("adm", true, "ROLE_ADMIN");

        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", admin)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(admin))
                .andExpect(jsonPath("$.data.authorities[0]").value("ROLE_ADMIN"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorities[0]").value("ROLE_ADMIN"));
    }

    @Test
    void rejectsInvalidAdminPaginationAndQueryBounds() throws Exception {
        String admin = createUser("adm", true, "ROLE_ADMIN");

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "-1")
                        .with(user(admin).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("size", "51")
                        .with(user(admin).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "abc")
                        .with(user(admin).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("query", "x".repeat(33))
                        .with(user(admin).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void statusChangesAffectNextLoginAndSelfDisableIsRejected() throws Exception {
        String admin = createUser("adm", true, "ROLE_ADMIN");
        String member = createUser("member", true, "ROLE_USER");

        mockMvc.perform(put("/api/v1/admin/users/{username}/status", member)
                        .with(user(admin).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(member))
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", member)
                        .param("password", PASSWORD))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/admin/users/{username}/status", member)
                        .with(user(admin).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", member)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorities[0]").value("ROLE_USER"));

        mockMvc.perform(put("/api/v1/admin/users/{username}/status", admin)
                        .with(user(admin).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        Boolean stillEnabled = jdbcTemplate.queryForObject(
                "SELECT enabled FROM app_users WHERE username = ?", Boolean.class, admin);
        org.junit.jupiter.api.Assertions.assertTrue(stillEnabled);
    }

    @Test
    void returnsNotFoundForUnknownAccount() throws Exception {
        String admin = createUser("adm", true, "ROLE_ADMIN");

        mockMvc.perform(put("/api/v1/admin/users/{username}/status", "missing-" + UUID.randomUUID())
                        .with(user(admin).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void moderatesFeedbackAndWritesAuditableGovernanceRecord() throws Exception {
        String admin = createUser("adm", true, "ROLE_ADMIN");
        String member = createUser("member", true, "ROLE_USER");
        long recommendationId = createRecommendation(member);
        jdbcTemplate.update(
                "INSERT INTO recommendation_feedback "
                        + "(recommendation_id, user_id, rating, feedback_type, comment, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                recommendationId,
                member,
                2,
                "too-warm",
                "希望增加更轻薄的方案",
                Timestamp.from(Instant.now()));

        mockMvc.perform(get("/api/v1/admin/feedback")
                        .param("status", "PENDING")
                        .param("query", member)
                        .with(user(admin).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].recommendationId").value(recommendationId))
                .andExpect(jsonPath("$.data.items[0].username").value(member))
                .andExpect(jsonPath("$.data.items[0].moderationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].comment").value("希望增加更轻薄的方案"))
                .andExpect(jsonPath("$.data.items[0].reason").doesNotExist());

        mockMvc.perform(put("/api/v1/admin/feedback/{recommendationId}/status", recommendationId)
                        .with(user(admin).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moderationStatus").value("REVIEWED"));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("action", "FEEDBACK_STATUS_UPDATE")
                        .with(user(admin).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].actorUsername").value(admin))
                .andExpect(jsonPath("$.data.items[0].targetType").value("FEEDBACK"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(String.valueOf(recommendationId)))
                .andExpect(jsonPath("$.data.items[0].details").value(org.hamcrest.Matchers.containsString("PENDING")));

        String moderationStatus = jdbcTemplate.queryForObject(
                "SELECT moderation_status FROM recommendation_feedback WHERE recommendation_id = ?",
                String.class,
                recommendationId);
        org.junit.jupiter.api.Assertions.assertEquals("REVIEWED", moderationStatus);
        String handledBy = jdbcTemplate.queryForObject(
                "SELECT handled_by FROM recommendation_feedback WHERE recommendation_id = ?",
                String.class,
                recommendationId);
        org.junit.jupiter.api.Assertions.assertEquals(admin, handledBy);

        mockMvc.perform(post("/api/v1/me/recommendations/{recommendationId}/feedback", recommendationId)
                        .with(user(member))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"feedbackType\":\"updated\",\"comment\":\"补充反馈\"}"))
                .andExpect(status().isOk());

        String resetStatus = jdbcTemplate.queryForObject(
                "SELECT moderation_status FROM recommendation_feedback WHERE recommendation_id = ?",
                String.class,
                recommendationId);
        org.junit.jupiter.api.Assertions.assertEquals("PENDING", resetStatus);
    }

    @Test
    void protectsFeedbackAndAuditFiltersFromOrdinaryUsersAndInvalidStatus() throws Exception {
        String member = createUser("member", true, "ROLE_USER");

        mockMvc.perform(get("/api/v1/admin/feedback")
                        .with(user(member).roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .with(user(member).roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        String admin = createUser("adm", true, "ROLE_ADMIN");
        mockMvc.perform(get("/api/v1/admin/feedback")
                        .param("status", "UNKNOWN")
                        .with(user(admin).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void keepsTrendContentIsolatedUntilHumanReviewAndProtectsModerationApi() throws Exception {
        String admin = createUser("adm", true, "ROLE_ADMIN");
        String member = createUser("member", true, "ROLE_USER");
        String trendId = "weibo:admin-moderation-" + UUID.randomUUID().toString().replace("-", "");
        Instant fetchedAt = Instant.now().minusSeconds(30);
        jdbcTemplate.update(
                "INSERT INTO trend_contents "
                        + "(id, platform, source_id, payload, published_at, fetched_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                trendId,
                "weibo",
                "test-source",
                "{\"id\":\"" + trendId + "\",\"platform\":\"weibo\",\"title\":\"后台审核测试\","
                        + "\"topicTags\":[\"通勤\"],\"heatScore\":72,\"publishedAt\":\""
                        + fetchedAt.toString() + "\",\"fetchedAt\":\"" + fetchedAt.toString()
                        + "\",\"sourceUrl\":\"https://example.com/trend\",\"stale\":false,"
                        + "\"imageUrl\":null,\"summary\":\"摘要\",\"evidence\":null}",
                Timestamp.from(fetchedAt),
                Timestamp.from(fetchedAt));

        mockMvc.perform(get("/api/v1/admin/trends/contents")
                        .with(user(member).roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/v1/admin/trends/contents")
                        .param("status", "PENDING_AI")
                        .param("query", "后台审核测试")
                        .with(user(admin).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].moderationStatus").value("PENDING_AI"))
                .andExpect(jsonPath("$.data.items[0].title").value("后台审核测试"));

        mockMvc.perform(post("/api/v1/admin/trends/ai-review")
                        .param("limit", "10")
                        .with(user(admin).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiEnabled").value(false))
                .andExpect(jsonPath("$.data.reviewed").value(0));

        mockMvc.perform(get("/api/v1/trends").param("period", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        jdbcTemplate.update(
                "UPDATE trend_contents SET moderation_status='PENDING_HUMAN', ai_decision='PASS', "
                        + "ai_risk_level='LOW', ai_reason='与穿搭内容相关', ai_model='qwen-plus-test', "
                        + "ai_prompt_version='trend-moderation-v1', ai_reviewed_at=? WHERE id=?",
                Timestamp.from(Instant.now()),
                trendId);

        mockMvc.perform(put("/api/v1/admin/trends/contents/{id}/review", trendId)
                        .with(user(admin).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"note\":\"人工确认来源与穿搭主题一致\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moderationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.hidden").value(false))
                .andExpect(jsonPath("$.data.reviewedBy").value(admin));

        mockMvc.perform(get("/api/v1/trends").param("period", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(trendId));

        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT moderation_status FROM trend_contents WHERE id = ?", String.class, trendId));
    }

    private long createRecommendation(String userId) {
        jdbcTemplate.update(
                "INSERT INTO recommendations "
                        + "(user_id, occasion, city, summary, reason, engine, saved, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                userId,
                "日常通勤",
                "长沙",
                "测试推荐摘要",
                "测试推荐理由",
                "development-rule-v1",
                false,
                Timestamp.from(Instant.now()));
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM recommendations WHERE user_id = ?",
                Long.class,
                userId);
    }

    private String createUser(String prefix, boolean enabled, String... authorities) {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        jdbcTemplate.update(
                "INSERT INTO app_users (username, password_hash, enabled) VALUES (?, ?, ?)",
                username,
                passwordEncoder.encode(PASSWORD),
                enabled);
        for (String authority : authorities) {
            jdbcTemplate.update(
                    "INSERT INTO app_authorities (username, authority) VALUES (?, ?)",
                    username,
                    authority);
        }
        return username;
    }
}
