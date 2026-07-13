package com.fashion.recommendation.style;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StyleProfileRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StyleProfileRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<StyleProfile> findByUserId(String userId) {
        return jdbcTemplate.query(
                        "SELECT display_name, style_preferences, color_preferences, occasion_preferences, style_tags, "
                                + "try_style_tags, color_suggestions, item_suggestions, reason_summary, model_name, updated_at "
                                + "FROM style_profiles WHERE user_id = ?",
                        (rs, rowNum) -> new StyleProfile(
                                rs.getString("display_name"),
                                readList(rs.getString("style_preferences")),
                                readList(rs.getString("color_preferences")),
                                readList(rs.getString("occasion_preferences")),
                                readList(rs.getString("style_tags")),
                                readList(rs.getString("try_style_tags")),
                                readList(rs.getString("color_suggestions")),
                                readList(rs.getString("item_suggestions")),
                                rs.getString("reason_summary"),
                                rs.getString("model_name"),
                                rs.getTimestamp("updated_at").toInstant(),
                                false),
                        userId)
                .stream()
                .findFirst();
    }

    public void save(String userId, StyleProfile profile) {
        int updated = jdbcTemplate.update(
                "UPDATE style_profiles SET display_name = ?, style_preferences = ?, color_preferences = ?, occasion_preferences = ?, "
                        + "style_tags = ?, try_style_tags = ?, color_suggestions = ?, item_suggestions = ?, reason_summary = ?, "
                        + "model_name = ?, updated_at = ? WHERE user_id = ?",
                profile.displayName(), writeList(profile.stylePreferences()), writeList(profile.colorPreferences()),
                writeList(profile.occasions()), writeList(profile.styleTags()), writeList(profile.tryStyleTags()),
                writeList(profile.colorSuggestions()), writeList(profile.itemSuggestions()), profile.reasonSummary(),
                profile.modelName(), Timestamp.from(profile.generatedAt()), userId);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO style_profiles (user_id, display_name, style_preferences, color_preferences, occasion_preferences, "
                            + "style_tags, try_style_tags, color_suggestions, item_suggestions, reason_summary, model_name, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    userId, profile.displayName(), writeList(profile.stylePreferences()), writeList(profile.colorPreferences()),
                    writeList(profile.occasions()), writeList(profile.styleTags()), writeList(profile.tryStyleTags()),
                    writeList(profile.colorSuggestions()), writeList(profile.itemSuggestions()), profile.reasonSummary(),
                    profile.modelName(), Timestamp.from(profile.generatedAt()));
        }
    }

    private List<String> readList(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("风格档案数据损坏", exception);
        }
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化风格档案", exception);
        }
    }
}
