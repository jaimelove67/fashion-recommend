package com.fashion.recommendation.wardrobe;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ImageCleanupRepository {
    private final JdbcTemplate jdbcTemplate;

    public ImageCleanupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void enqueue(String objectKey) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO image_cleanup_tasks (object_key, attempts, next_attempt_at, created_at) VALUES (?, 0, ?, ?)",
                objectKey, Timestamp.from(now), Timestamp.from(now));
    }

    public List<String> findDue(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT object_key FROM image_cleanup_tasks WHERE next_attempt_at <= CURRENT_TIMESTAMP "
                        + "ORDER BY next_attempt_at, object_key LIMIT ?",
                String.class, limit);
    }

    public void recordFailure(String objectKey) {
        jdbcTemplate.update(
                "UPDATE image_cleanup_tasks SET attempts = attempts + 1, next_attempt_at = ? WHERE object_key = ?",
                Timestamp.from(Instant.now().plusSeconds(300)), objectKey);
    }

    public void remove(String objectKey) {
        jdbcTemplate.update("DELETE FROM image_cleanup_tasks WHERE object_key = ?", objectKey);
    }
}
