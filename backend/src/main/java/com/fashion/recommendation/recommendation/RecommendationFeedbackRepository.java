package com.fashion.recommendation.recommendation;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationFeedbackRepository {
    private final JdbcTemplate jdbcTemplate;

    public RecommendationFeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RecommendationFeedback save(String userId, Long recommendationId, RecommendationFeedbackRequest request) {
        Instant updatedAt = Instant.now();
        int updated = jdbcTemplate.update(
                "UPDATE recommendation_feedback SET rating = ?, feedback_type = ?, comment = ?, updated_at = ? "
                        + "WHERE recommendation_id = ? AND user_id = ?",
                request.rating(), request.feedbackType(), request.comment(), Timestamp.from(updatedAt), recommendationId, userId);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO recommendation_feedback (recommendation_id, user_id, rating, feedback_type, comment, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    recommendationId, userId, request.rating(), request.feedbackType(), request.comment(), Timestamp.from(updatedAt));
        }
        return new RecommendationFeedback(request.rating(), request.feedbackType(), request.comment(), updatedAt);
    }

    public Optional<RecommendationFeedback> findByRecommendationId(String userId, Long recommendationId) {
        return jdbcTemplate.query(
                        "SELECT rating, feedback_type, comment, updated_at FROM recommendation_feedback "
                                + "WHERE recommendation_id = ? AND user_id = ?",
                        (rs, rowNum) -> new RecommendationFeedback(
                                rs.getInt("rating"),
                                rs.getString("feedback_type"),
                                rs.getString("comment"),
                                rs.getTimestamp("updated_at").toInstant()),
                        recommendationId, userId)
                .stream()
                .findFirst();
    }
}
