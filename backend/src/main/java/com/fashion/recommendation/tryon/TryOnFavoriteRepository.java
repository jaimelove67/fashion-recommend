package com.fashion.recommendation.tryon;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class TryOnFavoriteRepository {
    private final JdbcTemplate jdbc;

    public TryOnFavoriteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<TryOnFavorite> list(String userId) {
        return jdbc.query("SELECT * FROM try_on_favorites WHERE user_id=? ORDER BY created_at DESC,id DESC",
                (rs, row) -> map(rs), userId);
    }

    public Optional<TryOnFavorite> find(Long id, String userId) {
        return jdbc.query("SELECT * FROM try_on_favorites WHERE id=? AND user_id=?",
                (rs, row) -> map(rs), id, userId).stream().findFirst();
    }

    public Optional<TryOnFavorite> findFingerprint(String userId, String fingerprint) {
        return jdbc.query("SELECT * FROM try_on_favorites WHERE user_id=? AND fingerprint=?",
                (rs, row) -> map(rs), userId, fingerprint).stream().findFirst();
    }

    public TryOnFavorite create(String userId, String name, String category, String sourceUrl,
            String sourceKind, String objectKey, String fingerprint) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO try_on_favorites(user_id,name,category,source_url,source_kind,image_object_key,fingerprint,created_at) VALUES (?,?,?,?,?,?,?,?)",
                    new String[] {"id"});
            statement.setString(1, userId);
            statement.setString(2, name);
            statement.setString(3, category);
            statement.setString(4, sourceUrl);
            statement.setString(5, sourceKind);
            statement.setString(6, objectKey);
            statement.setString(7, fingerprint);
            statement.setTimestamp(8, Timestamp.from(Instant.now()));
            return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("收藏保存后未返回 ID");
        return find(keys.getKey().longValue(), userId).orElseThrow();
    }

    private static TryOnFavorite map(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        return new TryOnFavorite(id, rs.getString("name"), rs.getString("category"),
                "/api/v1/me/try-on-favorites/" + id + "/image", rs.getString("source_url"),
                rs.getString("source_kind"), rs.getTimestamp("created_at").toInstant(), rs.getString("image_object_key"));
    }
}
