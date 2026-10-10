package com.fashion.recommendation.style;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class StyleProfileRepository {
    private static final String FIELDS = "display_name, gender, style_preferences, color_preferences, occasion_preferences, "
            + "style_tags, try_style_tags, color_suggestions, item_suggestions, reason_summary, model_name, updated_at, "
            + "height_cm, weight_kg, photo_object_key, personal_analysis, analysis_source, analysis_model_name, "
            + "analysis_updated_at, analysis_stale, use_personal_photo_for_outfit, avoid_preferences, preferences_confirmed";
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StyleProfileRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<StyleProfile> findByUserId(String userId) {
        return jdbcTemplate.query("SELECT " + FIELDS + ", profile_revision FROM style_profiles WHERE user_id = ?",
                (rs, rowNum) -> {
                    String photoKey = rs.getString("photo_object_key");
                    Timestamp analysisTime = rs.getTimestamp("analysis_updated_at");
                    long revision = rs.getLong("profile_revision");
                    return new StyleProfile(
                            rs.getString("display_name"), rs.getString("gender"),
                            readList(rs.getString("style_preferences")), readList(rs.getString("color_preferences")),
                            readList(rs.getString("occasion_preferences")), readList(rs.getString("style_tags")),
                            readList(rs.getString("try_style_tags")), readList(rs.getString("color_suggestions")),
                            readList(rs.getString("item_suggestions")), rs.getString("reason_summary"),
                            rs.getString("model_name"), rs.getTimestamp("updated_at").toInstant(),
                            rs.getBoolean("analysis_stale"), number(rs.getObject("height_cm")), number(rs.getObject("weight_kg")),
                            photoKey == null ? null : "/api/v1/me/style-profile/photo?v=" + photoVersion(photoKey),
                            photoKey, readAnalysis(rs.getString("personal_analysis")), rs.getString("analysis_source"),
                            rs.getString("analysis_model_name"), analysisTime == null ? null : analysisTime.toInstant(), revision,
                            rs.getBoolean("use_personal_photo_for_outfit"), readList(rs.getString("avoid_preferences")),
                            rs.getBoolean("preferences_confirmed"));
                }, userId).stream().findFirst();
    }

    public void createIfMissing(String userId, StyleProfile profile) {
        List<Object> parameters = parameters(profile);
        parameters.add(0, userId);
        try {
            jdbcTemplate.update("INSERT INTO style_profiles (user_id, " + FIELDS + ") VALUES ("
                    + String.join(",", Collections.nCopies(parameters.size(), "?")) + ")", parameters.toArray());
        } catch (DuplicateKeyException ignored) {
            // Another request created the same user's default profile; keep that row.
        }
    }

    /** Compare-and-set prevents a slow analysis from replacing newer photos or manual corrections. */
    public void save(String userId, StyleProfile profile) {
        List<Object> parameters = parameters(profile);
        parameters.add(userId);
        parameters.add(profile.revision());
        String assignments = Arrays.stream(FIELDS.split(",\\s*")).map(field -> field + " = ?")
                .reduce((left, right) -> left + ", " + right).orElseThrow();
        int updated = jdbcTemplate.update("UPDATE style_profiles SET " + assignments
                + ", profile_revision = profile_revision + 1 WHERE user_id = ? AND profile_revision = ?", parameters.toArray());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "个人资料已更新，请刷新后重试");
        }
    }

    private List<Object> parameters(StyleProfile profile) {
        return new ArrayList<>(Arrays.asList(profile.displayName(), profile.gender(), write(profile.stylePreferences()),
                write(profile.colorPreferences()), write(profile.occasions()), write(profile.styleTags()),
                write(profile.tryStyleTags()), write(profile.colorSuggestions()), write(profile.itemSuggestions()),
                profile.reasonSummary(), profile.modelName(), Timestamp.from(profile.generatedAt()), profile.heightCm(),
                profile.weightKg(), profile.photoObjectKey(), profile.analysis() == null ? null : write(profile.analysis()),
                profile.analysisSource(), profile.analysisModelName(),
                profile.analysisUpdatedAt() == null ? null : Timestamp.from(profile.analysisUpdatedAt()), profile.stale(),
                profile.usePersonalPhotoForOutfit(), write(profile.avoidPreferences()), profile.preferencesConfirmed()));
    }

    private List<String> readList(String value) {
        try { return objectMapper.readValue(value, new TypeReference<List<String>>() { }); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("风格档案数据损坏", exception); }
    }

    private PersonalStyleAnalysis readAnalysis(String value) {
        if (value == null || value.isBlank()) return null;
        try { return objectMapper.readValue(value, PersonalStyleAnalysis.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("形象分析数据损坏", exception); }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("无法序列化风格档案", exception); }
    }

    private static Double number(Object value) { return value == null ? null : ((Number) value).doubleValue(); }

    private static String photoVersion(String photoKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(photoKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to calculate the photo version", exception);
        }
    }
}
