package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.weather.WeatherSnapshot;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationRepository {
    private static final String SELECT_RECORD = "SELECT id, occasion, city, temperature_c, summary, reason, engine, saved, created_at, "
            + "weather_apparent_temperature_c, weather_precipitation_mm, weather_code, weather_wind_speed_kmh, "
            + "weather_observed_at, weather_source FROM recommendations ";

    private final JdbcTemplate jdbcTemplate;

    public RecommendationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long create(
            String userId,
            RecommendationRequest request,
            WeatherSnapshot weather,
            String summary,
            String reason,
            String engine,
            Instant generatedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO recommendations (user_id, occasion, city, temperature_c, summary, reason, engine, "
                            + "weather_apparent_temperature_c, weather_precipitation_mm, weather_code, weather_wind_speed_kmh, "
                            + "weather_observed_at, weather_source, saved, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?)",
                    new String[] {"id"});
            statement.setString(1, userId);
            statement.setString(2, request.occasion().trim());
            statement.setString(3, request.city().trim());
            statement.setDouble(4, weather.temperatureC());
            statement.setString(5, summary);
            statement.setString(6, reason);
            statement.setString(7, engine);
            statement.setDouble(8, weather.apparentTemperatureC());
            statement.setDouble(9, weather.precipitationMm());
            statement.setInt(10, weather.weatherCode());
            statement.setDouble(11, weather.windSpeedKmh());
            statement.setTimestamp(12, Timestamp.from(weather.observedAt()));
            statement.setString(13, weather.source());
            statement.setTimestamp(14, Timestamp.from(generatedAt));
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("创建推荐后没有返回记录 ID");
        }
        return generatedId.longValue();
    }

    public void addItems(Long recommendationId, List<WardrobeItem> wardrobeItems) {
        AtomicInteger position = new AtomicInteger(0);
        jdbcTemplate.batchUpdate(
                "INSERT INTO recommendation_items (recommendation_id, position_no, wardrobe_item_id, name, category, color, style, image_url, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                wardrobeItems,
                wardrobeItems.size(),
                (statement, item) -> {
                    statement.setLong(1, recommendationId);
                    statement.setInt(2, position.getAndIncrement());
                    statement.setLong(3, item.id());
                    statement.setString(4, item.name());
                    statement.setString(5, item.category());
                    statement.setString(6, item.color());
                    statement.setString(7, item.style());
                    statement.setString(8, item.imageUrl());
                    statement.setTimestamp(9, Timestamp.from(item.createdAt()));
                });
    }

    public Optional<RecommendationRecord> findByIdForUser(Long id, String userId) {
        return jdbcTemplate.query(SELECT_RECORD + "WHERE id = ? AND user_id = ?", this::mapRecord, id, userId)
                .stream()
                .findFirst();
    }

    public List<RecommendationRecord> findAllByUserId(String userId) {
        return jdbcTemplate.query(SELECT_RECORD + "WHERE user_id = ? ORDER BY created_at DESC, id DESC", this::mapRecord, userId);
    }

    public List<WardrobeItem> findItems(Long recommendationId) {
        return jdbcTemplate.query(
                "SELECT wardrobe_item_id, name, category, color, style, image_url, created_at "
                        + "FROM recommendation_items WHERE recommendation_id = ? ORDER BY position_no",
                (rs, rowNum) -> new WardrobeItem(
                        rs.getObject("wardrobe_item_id", Long.class),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("color"),
                        rs.getString("style"),
                        rs.getString("image_url"),
                        rs.getTimestamp("created_at").toInstant()),
                recommendationId);
    }

    public boolean markSaved(Long id, String userId) {
        return jdbcTemplate.update("UPDATE recommendations SET saved = TRUE WHERE id = ? AND user_id = ?", id, userId) > 0;
    }

    private RecommendationRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        Timestamp weatherObservedAt = rs.getTimestamp("weather_observed_at");
        return new RecommendationRecord(
                rs.getLong("id"),
                rs.getString("occasion"),
                rs.getString("city"),
                nullableDouble(rs, "temperature_c"),
                rs.getString("summary"),
                rs.getString("reason"),
                rs.getString("engine"),
                rs.getBoolean("saved"),
                rs.getTimestamp("created_at").toInstant(),
                nullableDouble(rs, "weather_apparent_temperature_c"),
                nullableDouble(rs, "weather_precipitation_mm"),
                rs.getObject("weather_code", Integer.class),
                nullableDouble(rs, "weather_wind_speed_kmh"),
                weatherObservedAt == null ? null : weatherObservedAt.toInstant(),
                rs.getString("weather_source"));
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        java.math.BigDecimal value = resultSet.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }
}
