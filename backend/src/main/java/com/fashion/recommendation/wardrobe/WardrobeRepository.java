package com.fashion.recommendation.wardrobe;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class WardrobeRepository {
    private static final String SELECT_COLUMNS = "id, name, category, color, style, image_url, created_at, "
            + "recognition_status, recognition_message, image_object_key";

    private final JdbcTemplate jdbcTemplate;

    public WardrobeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WardrobeItem> findByUserId(String userId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM wardrobe_items WHERE user_id = ? ORDER BY created_at DESC, id DESC",
                (rs, rowNum) -> mapItem(rs.getLong("id"), rs.getString("name"), rs.getString("category"),
                        rs.getString("color"), rs.getString("style"), rs.getString("image_url"),
                        rs.getTimestamp("created_at"), rs.getString("recognition_status"),
                        rs.getString("recognition_message"), rs.getString("image_object_key")),
                userId);
    }

    public Optional<WardrobeItem> findByIdForUser(Long id, String userId) {
        return jdbcTemplate.query(
                        "SELECT " + SELECT_COLUMNS + " FROM wardrobe_items WHERE id = ? AND user_id = ?",
                        (rs, rowNum) -> mapItem(rs.getLong("id"), rs.getString("name"), rs.getString("category"),
                                rs.getString("color"), rs.getString("style"), rs.getString("image_url"),
                                rs.getTimestamp("created_at"), rs.getString("recognition_status"),
                                rs.getString("recognition_message"), rs.getString("image_object_key")),
                        id, userId)
                .stream()
                .findFirst();
    }

    public WardrobeItem create(String userId, WardrobeItemRequest request) {
        return create(userId, request, null, "MANUAL", null);
    }

    public WardrobeItem create(
            String userId,
            WardrobeItemRequest request,
            String imageObjectKey,
            String recognitionStatus,
            String recognitionMessage) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO wardrobe_items (user_id, name, category, color, style, image_url, image_object_key, "
                            + "recognition_status, recognition_message, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[] {"id"});
            statement.setString(1, userId);
            statement.setString(2, request.name().trim());
            statement.setString(3, request.category().trim());
            statement.setString(4, request.color().trim());
            statement.setString(5, blankToNull(request.style()));
            statement.setString(6, blankToNull(request.imageUrl()));
            statement.setString(7, blankToNull(imageObjectKey));
            statement.setString(8, recognitionStatus);
            statement.setString(9, blankToNull(recognitionMessage));
            statement.setTimestamp(10, Timestamp.from(now));
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("创建衣物后没有返回记录 ID");
        }
        return findByIdForUser(generatedId.longValue(), userId)
                .orElseThrow(() -> new IllegalStateException("创建衣物后无法读取记录"));
    }

    public void updateImageUrl(Long id, String userId, String imageUrl) {
        jdbcTemplate.update("UPDATE wardrobe_items SET image_url = ? WHERE id = ? AND user_id = ?",
                imageUrl, id, userId);
    }

    public WardrobeItem updateMetadata(Long id, String userId, WardrobeItemRequest request) {
        int updated = jdbcTemplate.update(
                "UPDATE wardrobe_items SET name = ?, category = ?, color = ?, style = ?, "
                        + "recognition_status = 'MANUAL_CORRECTED', recognition_message = NULL "
                        + "WHERE id = ? AND user_id = ?",
                request.name().trim(), request.category().trim(), request.color().trim(), blankToNull(request.style()),
                id, userId);
        return updated == 0 ? null : findByIdForUser(id, userId).orElseThrow();
    }

    public Optional<WardrobeItem> findByImageForUser(Long id, String userId) {
        return findByIdForUser(id, userId).filter(item -> item.imageObjectKey() != null);
    }

    public boolean delete(Long id, String userId) {
        return jdbcTemplate.update("DELETE FROM wardrobe_items WHERE id = ? AND user_id = ?", id, userId) > 0;
    }

    private static WardrobeItem mapItem(
            Long id,
            String name,
            String category,
            String color,
            String style,
            String imageUrl,
            Timestamp createdAt,
            String recognitionStatus,
            String recognitionMessage,
            String imageObjectKey) {
        return new WardrobeItem(id, name, category, color, style, imageUrl, createdAt.toInstant(),
                recognitionStatus, recognitionMessage, imageObjectKey);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
