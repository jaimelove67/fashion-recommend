package com.fashion.recommendation.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class AdminFeedbackTransactionTest {
    @Autowired
    private AdminService adminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rollsBackFeedbackStatusWhenAuditInsertFails() {
        String testId = UUID.randomUUID().toString().replace("-", "");
        String userId = "feedback-txn-" + testId;
        String summary = "feedback-txn-" + testId;
        String failedActor = "fail-" + testId.substring(0, 20);
        String successfulActor = "ok-" + testId.substring(20, 32);
        String auditFailureConstraint = "ck_test_reject_feedback_audit_" + testId;
        Long recommendationId = null;

        try {
            jdbcTemplate.update(
                    "INSERT INTO recommendations "
                            + "(user_id, occasion, city, summary, reason, engine, saved, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, FALSE, ?)",
                    userId,
                    "日常通勤",
                    "长沙",
                    summary,
                    "事务回滚验证",
                    "development-rule-v1",
                    Timestamp.from(Instant.now()));
            recommendationId = jdbcTemplate.queryForObject(
                    "SELECT id FROM recommendations WHERE user_id = ? AND summary = ?",
                    Long.class,
                    userId,
                    summary);
            jdbcTemplate.update(
                    "INSERT INTO recommendation_feedback "
                            + "(recommendation_id, user_id, rating, feedback_type, comment, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    recommendationId,
                    userId,
                    2,
                    "too-warm",
                    "事务回滚验证",
                    Timestamp.from(Instant.now()));
            Long createdRecommendationId = recommendationId;

            jdbcTemplate.execute("ALTER TABLE admin_audit_logs ADD CONSTRAINT " + auditFailureConstraint
                    + " CHECK (action <> 'FEEDBACK_STATUS_UPDATE' OR actor_username <> '" + failedActor + "')");

            assertThrows(
                    DataAccessException.class,
                    () -> adminService.updateFeedbackStatus(failedActor, createdRecommendationId, "REVIEWED"));

            assertEquals("PENDING", jdbcTemplate.queryForObject(
                    "SELECT moderation_status FROM recommendation_feedback WHERE recommendation_id = ?",
                    String.class,
                    recommendationId));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM admin_audit_logs "
                            + "WHERE action = 'FEEDBACK_STATUS_UPDATE' AND target_id = ?",
                    Integer.class,
                    String.valueOf(recommendationId)));

            AdminFeedback updated = adminService.updateFeedbackStatus(
                    successfulActor, createdRecommendationId, "REVIEWED");
            assertEquals("REVIEWED", updated.moderationStatus());
            assertEquals("REVIEWED", jdbcTemplate.queryForObject(
                    "SELECT moderation_status FROM recommendation_feedback WHERE recommendation_id = ?",
                    String.class,
                    recommendationId));
            assertEquals(successfulActor, jdbcTemplate.queryForObject(
                    "SELECT actor_username FROM admin_audit_logs "
                            + "WHERE action = 'FEEDBACK_STATUS_UPDATE' AND target_id = ?",
                    String.class,
                    String.valueOf(recommendationId)));
        } finally {
            jdbcTemplate.execute("ALTER TABLE admin_audit_logs DROP CONSTRAINT IF EXISTS "
                    + auditFailureConstraint);
            if (recommendationId != null) {
                jdbcTemplate.update("DELETE FROM recommendation_feedback WHERE recommendation_id = ?", recommendationId);
                jdbcTemplate.update("DELETE FROM recommendations WHERE id = ?", recommendationId);
                jdbcTemplate.update(
                        "DELETE FROM admin_audit_logs WHERE target_id = ?",
                        String.valueOf(recommendationId));
            }
        }
    }
}
