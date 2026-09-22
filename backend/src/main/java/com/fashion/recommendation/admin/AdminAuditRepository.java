package com.fashion.recommendation.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAuditRepository {
    private final JdbcTemplate jdbcTemplate;

    public AdminAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void append(
            String actorUsername,
            String action,
            String targetType,
            String targetId,
            String outcome,
            String details) {
        String safeDetails = details == null ? null : details.substring(0, Math.min(details.length(), 500));
        jdbcTemplate.update(
                "INSERT INTO admin_audit_logs "
                        + "(actor_username, action, target_type, target_id, outcome, details, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                actorUsername,
                action,
                targetType,
                targetId,
                outcome,
                safeDetails,
                Timestamp.from(Instant.now()));
    }

    public long count(String action, String outcome) {
        QuerySpec query = buildQuery("SELECT COUNT(*) FROM admin_audit_logs", action, outcome);
        Long count = jdbcTemplate.queryForObject(query.sql(), Long.class, query.parameters().toArray());
        return count == null ? 0 : count;
    }

    public List<AdminAuditLog> findPage(String action, String outcome, int size, long offset) {
        QuerySpec query = buildQuery(
                "SELECT id, actor_username, action, target_type, target_id, outcome, details, created_at "
                        + "FROM admin_audit_logs",
                action,
                outcome);
        List<Object> parameters = new ArrayList<>(query.parameters());
        parameters.add(size);
        parameters.add(offset);
        return jdbcTemplate.query(
                query.sql() + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                (resultSet, rowNum) -> new AdminAuditLog(
                        resultSet.getLong("id"),
                        resultSet.getString("actor_username"),
                        resultSet.getString("action"),
                        resultSet.getString("target_type"),
                        resultSet.getString("target_id"),
                        resultSet.getString("outcome"),
                        resultSet.getString("details"),
                        resultSet.getTimestamp("created_at").toInstant()),
                parameters.toArray());
    }

    private static QuerySpec buildQuery(String selectSql, String action, String outcome) {
        StringBuilder sql = new StringBuilder(selectSql).append(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (action != null) {
            sql.append(" AND action = ?");
            parameters.add(action);
        }
        if (outcome != null) {
            sql.append(" AND outcome = ?");
            parameters.add(outcome);
        }
        return new QuerySpec(sql.toString(), parameters);
    }

    private record QuerySpec(String sql, List<Object> parameters) {
    }
}
