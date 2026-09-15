package com.fashion.recommendation.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {
    private final JdbcTemplate jdbcTemplate;

    public AdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminOverview overview() {
        return jdbcTemplate.queryForObject(
                "SELECT "
                        + "(SELECT COUNT(*) FROM app_users) AS total_users, "
                        + "(SELECT COUNT(*) FROM app_users WHERE enabled = TRUE) AS enabled_users, "
                        + "(SELECT COUNT(*) FROM wardrobe_items) AS total_wardrobe_items, "
                        + "(SELECT COUNT(*) FROM recommendations) AS total_recommendations, "
                        + "(SELECT COUNT(*) FROM recommendation_feedback) AS total_feedback, "
                        + "(SELECT COALESCE(AVG(rating), 0) FROM recommendation_feedback) AS average_rating, "
                        + "(SELECT COUNT(*) FROM recommendations WHERE saved = TRUE) AS saved_recommendations, "
                        + "(SELECT COUNT(*) FROM recommendations WHERE engine = 'llm') AS llm_recommendations, "
                        + "(SELECT COUNT(*) FROM recommendations WHERE fallback_reason IS NOT NULL) AS fallback_recommendations, "
                        + "(SELECT COUNT(*) FROM recommendation_feedback WHERE moderation_status = 'PENDING') AS pending_feedback, "
                        + "(SELECT COUNT(*) FROM wardrobe_items WHERE recognition_status = 'NEEDS_MANUAL_REVIEW') AS manual_review_items, "
                        + "(SELECT COUNT(*) FROM image_cleanup_tasks) AS pending_image_cleanup_tasks",
                (resultSet, rowNum) -> {
                    long totalUsers = resultSet.getLong("total_users");
                    long enabledUsers = resultSet.getLong("enabled_users");
                    return new AdminOverview(
                            totalUsers,
                            enabledUsers,
                            totalUsers - enabledUsers,
                            resultSet.getLong("total_wardrobe_items"),
                            resultSet.getLong("total_recommendations"),
                            resultSet.getLong("total_feedback"),
                            resultSet.getDouble("average_rating"),
                            resultSet.getLong("saved_recommendations"),
                            resultSet.getLong("llm_recommendations"),
                            resultSet.getLong("fallback_recommendations"),
                            resultSet.getLong("pending_feedback"),
                            resultSet.getLong("manual_review_items"),
                            resultSet.getLong("pending_image_cleanup_tasks"));
                });
    }

    public long countUsers(String queryPattern) {
        if (queryPattern == null) {
            return count("SELECT COUNT(*) FROM app_users");
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE LOWER(username) LIKE ? ESCAPE '\\'",
                Long.class,
                queryPattern);
        return count == null ? 0 : count;
    }

    public List<AdminUser> findUsers(String queryPattern, int size, long offset) {
        StringBuilder sql = new StringBuilder("SELECT username, enabled FROM app_users");
        List<Object> parameters = new ArrayList<>();
        if (queryPattern != null) {
            sql.append(" WHERE LOWER(username) LIKE ? ESCAPE '\\'");
            parameters.add(queryPattern);
        }
        sql.append(" ORDER BY username LIMIT ? OFFSET ?");
        parameters.add(size);
        parameters.add(offset);

        List<AdminUserRow> rows = jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> new AdminUserRow(
                        resultSet.getString("username"),
                        resultSet.getBoolean("enabled")),
                parameters.toArray());
        return attachAuthorities(rows);
    }

    public Optional<AdminUser> findByUsername(String username) {
        List<AdminUserRow> rows = jdbcTemplate.query(
                "SELECT username, enabled FROM app_users WHERE username = ?",
                (resultSet, rowNum) -> new AdminUserRow(
                        resultSet.getString("username"),
                        resultSet.getBoolean("enabled")),
                username);
        return attachAuthorities(rows).stream().findFirst();
    }

    public boolean updateEnabled(String username, boolean enabled) {
        return jdbcTemplate.update(
                "UPDATE app_users SET enabled = ? WHERE username = ?",
                enabled,
                username) > 0;
    }

    private List<AdminUser> attachAuthorities(List<AdminUserRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));
        Map<String, List<String>> authoritiesByUser = new HashMap<>();
        jdbcTemplate.query(
                "SELECT username, authority FROM app_authorities WHERE username IN ("
                        + placeholders + ") ORDER BY username, authority",
                (RowCallbackHandler) resultSet -> authoritiesByUser
                        .computeIfAbsent(resultSet.getString("username"), ignored -> new ArrayList<>())
                        .add(resultSet.getString("authority")),
                rows.stream().map(AdminUserRow::username).toArray());

        return rows.stream()
                .map(row -> new AdminUser(
                        row.username(),
                        row.enabled(),
                        authoritiesByUser.getOrDefault(row.username(), List.of())))
                .toList();
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private record AdminUserRow(String username, boolean enabled) {
    }
}
