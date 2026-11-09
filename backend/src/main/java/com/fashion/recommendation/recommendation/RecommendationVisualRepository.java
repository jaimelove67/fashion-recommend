package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.storage.StoredImageData;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class RecommendationVisualRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public RecommendationVisualRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public Optional<Visual> find(Long recommendationId, String fingerprint) {
        return jdbc.query("SELECT asset_id,model,generation_token,lease_until FROM recommendation_visuals "
                        + "WHERE recommendation_id=? AND fingerprint=?",
                (rs, row) -> new Visual(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant()),
                recommendationId, fingerprint).stream().findFirst();
    }

    // Commit a short reservation before calling the provider. No DB lock is held during generation.
    public String reserve(Long recommendationId, String fingerprint, boolean force) {
        return transactions.execute(ignored -> {
            jdbc.queryForObject("SELECT id FROM recommendations WHERE id=? FOR UPDATE", Long.class, recommendationId);
            var previous = find(recommendationId, fingerprint);
            if (previous.isPresent() && (previous.get().pending() || (!force && previous.get().assetId() != null))) return null;
            String userId = jdbc.queryForObject("SELECT user_id FROM recommendations WHERE id=?", String.class, recommendationId);
            jdbc.queryForObject("SELECT username FROM app_users WHERE username=? FOR UPDATE", String.class, userId);
            var quota = quota(userId);
            if (quota.dailyRemaining() == 0 || quota.weeklyRemaining() == 0) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "生图额度已用完，每天最多1张、每周最多4张（北京时间，周一重置）。");
            }
            String token = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO recommendation_visual_usage(token,user_id,reserved_at) VALUES (?,?,?)",
                    token, userId, Timestamp.from(Instant.now()));
            Timestamp lease = Timestamp.from(Instant.now().plusSeconds(900));
            if (previous.isEmpty()) {
                jdbc.update("INSERT INTO recommendation_visuals(recommendation_id,fingerprint,generation_token,lease_until) VALUES (?,?,?,?)",
                        recommendationId, fingerprint, token, lease);
            } else {
                jdbc.update("UPDATE recommendation_visuals SET generation_token=?,lease_until=? WHERE recommendation_id=? AND fingerprint=?",
                        token, lease, recommendationId, fingerprint);
            }
            return token;
        });
    }

    public boolean finish(Long recommendationId, String fingerprint, String token, StoredImageData image, String model) {
        return transactions.execute(ignored -> {
            boolean saved = jdbc.update("UPDATE recommendation_visuals SET asset_id=?,image_content=?,content_type=?,model=?,"
                            + "generation_token=NULL,lease_until=NULL WHERE recommendation_id=? AND fingerprint=? AND generation_token=?",
                    token, image.content(), image.contentType(), model, recommendationId, fingerprint, token) == 1;
            if (saved) jdbc.update("UPDATE recommendation_visual_usage SET completed=TRUE WHERE token=?", token);
            return saved;
        });
    }

    public void release(Long recommendationId, String fingerprint, String token) {
        transactions.executeWithoutResult(ignored -> {
            jdbc.update("UPDATE recommendation_visuals SET generation_token=NULL,lease_until=NULL "
                    + "WHERE recommendation_id=? AND fingerprint=? AND generation_token=?", recommendationId, fingerprint, token);
            jdbc.update("DELETE FROM recommendation_visual_usage WHERE token=? AND completed=FALSE", token);
        });
    }

    public Quota quota(String userId) {
        var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        var week = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        var zone = java.time.ZoneId.of("Asia/Shanghai");
        Integer daily = usageSince(userId, today.atStartOfDay(zone).toInstant());
        Integer weekly = usageSince(userId, week.atStartOfDay(zone).toInstant());
        return new Quota(Math.max(0, 1 - daily), Math.max(0, 4 - weekly));
    }

    private Integer usageSince(String userId, Instant start) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_visual_usage WHERE user_id=? AND reserved_at>=? "
                + "AND (completed=TRUE OR reserved_at>=?)", Integer.class,
                userId, Timestamp.from(start), Timestamp.from(Instant.now().minusSeconds(900)));
    }

    public record Quota(int dailyRemaining, int weeklyRemaining) {}

    public Optional<StoredImageData> image(String userId, Long recommendationId, String assetId) {
        return jdbc.query("SELECT v.image_content,v.content_type FROM recommendation_visuals v "
                        + "JOIN recommendations r ON r.id=v.recommendation_id "
                        + "WHERE r.user_id=? AND r.id=? AND v.asset_id=? AND v.image_content IS NOT NULL",
                (rs, row) -> new StoredImageData(rs.getBytes(1), rs.getString(2)),
                userId, recommendationId, assetId).stream().findFirst();
    }

    public record Visual(String assetId, String model, String token, Instant leaseUntil) {
        public boolean pending() { return token != null && leaseUntil != null && leaseUntil.isAfter(Instant.now()); }
    }
}
