package com.fashion.recommendation.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminFeedbackRepository {
    private static final String BASE_SELECT = "SELECT f.recommendation_id, f.user_id, f.rating, "
            + "f.feedback_type, f.comment, f.moderation_status, r.occasion, r.city, r.engine, "
            + "r.fallback_reason, f.updated_at "
            + "FROM recommendation_feedback f "
            + "JOIN recommendations r ON r.id = f.recommendation_id ";

    private final JdbcTemplate jdbcTemplate;

    public AdminFeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long count(String status, String queryPattern) {
        QuerySpec query = buildQuery(
                "SELECT COUNT(*) FROM recommendation_feedback f "
                        + "JOIN recommendations r ON r.id = f.recommendation_id ",
                status,
                queryPattern);
        Long count = jdbcTemplate.queryForObject(query.sql(), Long.class, query.parameters().toArray());
        return count == null ? 0 : count;
    }

    public List<AdminFeedback> findPage(String status, String queryPattern, int size, long offset) {
        QuerySpec query = buildQuery(BASE_SELECT, status, queryPattern);
        String sql = query.sql() + " ORDER BY f.updated_at DESC, f.recommendation_id DESC LIMIT ? OFFSET ?";
        List<Object> parameters = new ArrayList<>(query.parameters());
        parameters.add(size);
        parameters.add(offset);
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> map(resultSet), parameters.toArray());
    }

    public Optional<AdminFeedback> findByRecommendationId(long recommendationId) {
        return jdbcTemplate.query(
                        BASE_SELECT + "WHERE f.recommendation_id = ?",
                        (resultSet, rowNum) -> map(resultSet),
                        recommendationId)
                .stream()
                .findFirst();
    }

    public boolean updateStatus(long recommendationId, String status, String handledBy, Instant handledAt) {
        return jdbcTemplate.update(
                "UPDATE recommendation_feedback SET moderation_status = ?, handled_by = ?, handled_at = ? "
                        + "WHERE recommendation_id = ?",
                status,
                handledBy,
                handledAt == null ? null : Timestamp.from(handledAt),
                recommendationId) > 0;
    }

    private static QuerySpec buildQuery(String baseSql, String status, String queryPattern) {
        StringBuilder sql = new StringBuilder(baseSql).append("WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (status != null) {
            sql.append(" AND f.moderation_status = ?");
            parameters.add(status);
        }
        if (queryPattern != null) {
            sql.append(" AND (LOWER(f.user_id) LIKE ? ESCAPE '\\' "
                    + "OR LOWER(COALESCE(f.comment, '')) LIKE ? ESCAPE '\\')");
            parameters.add(queryPattern);
            parameters.add(queryPattern);
        }
        return new QuerySpec(sql.toString(), parameters);
    }

    private static AdminFeedback map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new AdminFeedback(
                resultSet.getLong("recommendation_id"),
                resultSet.getString("user_id"),
                resultSet.getInt("rating"),
                resultSet.getString("feedback_type"),
                resultSet.getString("comment"),
                resultSet.getString("moderation_status"),
                resultSet.getString("occasion"),
                resultSet.getString("city"),
                resultSet.getString("engine"),
                resultSet.getString("fallback_reason"),
                updatedAt == null ? null : updatedAt.toInstant());
    }

    private record QuerySpec(String sql, List<Object> parameters) {
    }
}
