package com.fashion.recommendation.trend;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TrendRepository {
    private static final String MODERATION_SELECT = "SELECT id, payload, hidden, moderation_status, ai_decision, "
            + "ai_risk_level, ai_reason, ai_model, ai_provider_call_id, ai_prompt_version, ai_reviewed_at, "
            + "reviewed_by, reviewed_at, review_note FROM trend_contents";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public TrendRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public void save(String source, TrendItem item) {
        Timestamp observedAt = Timestamp.from(item.fetchedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        String payload;
        try { payload = mapper.writeValueAsString(item); }
        catch (Exception e) { throw new IllegalArgumentException("趋势序列化失败", e); }
        int updated = jdbc.update("UPDATE trend_contents SET source_id=?,payload=?,published_at=?,fetched_at=? WHERE id=? AND fetched_at<=?",
                source, payload, Timestamp.from(item.publishedAt()), observedAt, item.id(), observedAt);
        if (updated == 0 && jdbc.queryForObject("SELECT COUNT(*) FROM trend_contents WHERE id=?", Integer.class, item.id()) == 0) {
            jdbc.update("INSERT INTO trend_contents(id,platform,source_id,payload,published_at,fetched_at) VALUES (?,?,?,?,?,?)",
                    item.id(), item.platform(), source, payload, Timestamp.from(item.publishedAt()), observedAt);
        }
        TrendEvidence e = item.evidence();
        if (e != null && e.hasCounters()
                && jdbc.queryForObject("SELECT COUNT(*) FROM trend_snapshots WHERE content_id=? AND observed_at=?", Integer.class,
                    item.id(), observedAt) == 0) {
            jdbc.update("INSERT INTO trend_snapshots(content_id,observed_at,likes,favorites,comments,reposts) VALUES (?,?,?,?,?,?)",
                    item.id(), observedAt, e.likes(), e.favorites(), e.comments(), e.reposts());
        }
    }

    public List<TrendItem> since(Instant cutoff) {
        return jdbc.query("SELECT payload FROM trend_contents WHERE hidden=FALSE AND moderation_status='APPROVED' "
                        + "AND fetched_at>=? ORDER BY published_at DESC LIMIT 1000",
                (rs, row) -> decode(rs.getString(1)), Timestamp.from(cutoff));
    }

    public Optional<TrendItem> find(String id) {
        return jdbc.query("SELECT payload FROM trend_contents WHERE id=? AND hidden=FALSE AND moderation_status='APPROVED'",
                (rs, row) -> decode(rs.getString(1)), id).stream().findFirst();
    }

    public List<TrendModerationItem> pendingAi(int limit) {
        return jdbc.query(MODERATION_SELECT + " WHERE moderation_status=? ORDER BY fetched_at ASC, id ASC LIMIT ?",
                this::mapModeration, TrendModerationStatus.PENDING_AI, limit);
    }

    public long countModeration(String status, String queryPattern) {
        QuerySpec query = buildModerationQuery("SELECT COUNT(*) FROM trend_contents", status, queryPattern);
        Long count = jdbc.queryForObject(query.sql(), Long.class, query.parameters().toArray());
        return count == null ? 0 : count;
    }

    public List<TrendModerationItem> findModerationPage(
            String status,
            String queryPattern,
            int size,
            long offset) {
        QuerySpec query = buildModerationQuery(MODERATION_SELECT, status, queryPattern);
        List<Object> parameters = new ArrayList<>(query.parameters());
        parameters.add(size);
        parameters.add(offset);
        return jdbc.query(
                query.sql() + " ORDER BY published_at DESC, id DESC LIMIT ? OFFSET ?",
                this::mapModeration,
                parameters.toArray());
    }

    public Optional<TrendModerationItem> findModeration(String id) {
        return jdbc.query(MODERATION_SELECT + " WHERE id=?", this::mapModeration, id).stream().findFirst();
    }

    public long countByModerationStatus(String status) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM trend_contents WHERE moderation_status=?",
                Long.class,
                status);
        return count == null ? 0 : count;
    }

    @Transactional
    public boolean markAiReviewed(String id, TrendAiReviewResult result, Instant reviewedAt) {
        return jdbc.update(
                "UPDATE trend_contents SET moderation_status=?, ai_decision=?, ai_risk_level=?, ai_reason=?, "
                        + "ai_model=?, ai_provider_call_id=?, ai_prompt_version=?, ai_reviewed_at=? "
                        + "WHERE id=? AND moderation_status=?",
                TrendModerationStatus.PENDING_HUMAN,
                result.decision(),
                result.riskLevel(),
                result.reason(),
                result.modelName(),
                result.providerCallId(),
                result.promptVersion(),
                Timestamp.from(reviewedAt),
                id,
                TrendModerationStatus.PENDING_AI) > 0;
    }

    @Transactional
    public boolean markAiFailed(String id, String reason, Instant reviewedAt) {
        return jdbc.update(
                "UPDATE trend_contents SET moderation_status=?, ai_decision=?, ai_risk_level=?, ai_reason=?, "
                        + "ai_model=NULL, ai_provider_call_id=NULL, ai_prompt_version=NULL, ai_reviewed_at=? "
                        + "WHERE id=? AND moderation_status=?",
                TrendModerationStatus.AI_FAILED,
                "ERROR",
                "UNKNOWN",
                reason,
                Timestamp.from(reviewedAt),
                id,
                TrendModerationStatus.PENDING_AI) > 0;
    }

    @Transactional
    public boolean retryAi(String id) {
        return jdbc.update(
                "UPDATE trend_contents SET moderation_status=?, hidden=TRUE, ai_decision=NULL, ai_risk_level=NULL, "
                        + "ai_reason=NULL, ai_model=NULL, ai_provider_call_id=NULL, ai_prompt_version=NULL, "
                        + "ai_reviewed_at=NULL, reviewed_by=NULL, reviewed_at=NULL, review_note=NULL "
                        + "WHERE id=? AND moderation_status=?",
                TrendModerationStatus.PENDING_AI,
                id,
                TrendModerationStatus.AI_FAILED) > 0;
    }

    @Transactional
    public boolean finalizeHuman(
            String id,
            String status,
            String actor,
            Instant reviewedAt,
            String note) {
        boolean approved = TrendModerationStatus.APPROVED.equals(status);
        return jdbc.update(
                "UPDATE trend_contents SET moderation_status=?, hidden=?, reviewed_by=?, reviewed_at=?, review_note=? "
                        + "WHERE id=? AND moderation_status IN (?, ?)",
                status,
                !approved,
                actor,
                Timestamp.from(reviewedAt),
                note,
                id,
                TrendModerationStatus.PENDING_HUMAN,
                TrendModerationStatus.AI_FAILED) > 0;
    }

    public Long growth(TrendItem item, Instant cutoff) {
        TrendEvidence now = item.evidence();
        if (now == null || !now.hasCounters()) return null;
        // A baseline must be at/before the window start; partial history cannot claim daily/weekly growth.
        var baseline = jdbc.query("SELECT likes,favorites,comments,reposts FROM trend_snapshots WHERE content_id=? AND observed_at<=? ORDER BY observed_at DESC LIMIT 1",
                (rs, row) -> new Long[] {(Long) rs.getObject(1), (Long) rs.getObject(2), (Long) rs.getObject(3), (Long) rs.getObject(4)},
                item.id(), Timestamp.from(cutoff));
        if (baseline.isEmpty()) return null;
        Long[] current = {now.likes(), now.favorites(), now.comments(), now.reposts()};
        long delta = 0;
        boolean comparable = false;
        for (int i = 0; i < current.length; i++) {
            if ((current[i] == null) != (baseline.get(0)[i] == null)) return null;
            if (current[i] != null) {
                if (current[i] < baseline.get(0)[i]) return null;
                delta += current[i] - baseline.get(0)[i];
                comparable = true;
            }
        }
        return comparable ? delta : null;
    }

    @Transactional
    public void status(String id, Instant at, boolean success, int count, String message) {
        if (jdbc.update("UPDATE trend_source_status SET last_attempt_at=?,state=?,message=?,item_count=? WHERE id=?",
                Timestamp.from(at), success ? "ready" : "unavailable", message, count, id) == 0) {
            jdbc.update("INSERT INTO trend_source_status(id,last_attempt_at,state,message,item_count) VALUES (?,?,?,?,?)",
                    id, Timestamp.from(at), success ? "ready" : "unavailable", message, count);
        }
        if (success) jdbc.update("UPDATE trend_source_status SET last_success_at=? WHERE id=?", Timestamp.from(at), id);
    }

    public List<TrendSourceStatus> statuses() {
        return jdbc.query("SELECT * FROM trend_source_status ORDER BY id", (rs, row) -> new TrendSourceStatus(
                rs.getString("id"), rs.getTimestamp("last_attempt_at").toInstant(),
                rs.getTimestamp("last_success_at") == null ? null : rs.getTimestamp("last_success_at").toInstant(),
                rs.getString("state"), rs.getString("message"), rs.getInt("item_count")));
    }

    public void hide(String id, boolean hidden) {
        if (!updateVisibility(id, hidden))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "内容不存在");
    }

    public boolean updateVisibility(String id, boolean hidden) {
        return jdbc.update("UPDATE trend_contents SET hidden=? WHERE id=?", hidden, id) > 0;
    }

    private TrendModerationItem mapModeration(ResultSet rs, int row) throws SQLException {
        TrendItem item = decode(rs.getString("payload"));
        return new TrendModerationItem(
                item.id(),
                item.platform(),
                item.title(),
                item.topicTags(),
                item.heatScore(),
                item.publishedAt(),
                item.fetchedAt(),
                item.sourceUrl(),
                rs.getBoolean("hidden"),
                item.imageUrl(),
                item.summary(),
                rs.getString("moderation_status"),
                rs.getString("ai_decision"),
                rs.getString("ai_risk_level"),
                rs.getString("ai_reason"),
                rs.getString("ai_model"),
                rs.getString("ai_provider_call_id"),
                rs.getString("ai_prompt_version"),
                instant(rs, "ai_reviewed_at"),
                rs.getString("reviewed_by"),
                instant(rs, "reviewed_at"),
                rs.getString("review_note"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static QuerySpec buildModerationQuery(String selectSql, String status, String queryPattern) {
        StringBuilder sql = new StringBuilder(selectSql).append(" WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (status != null) {
            sql.append(" AND moderation_status=?");
            parameters.add(status);
        }
        if (queryPattern != null) {
            sql.append(" AND (LOWER(id) LIKE ? ESCAPE '\\' OR LOWER(platform) LIKE ? ESCAPE '\\' "
                    + "OR LOWER(payload) LIKE ? ESCAPE '\\')");
            parameters.add(queryPattern);
            parameters.add(queryPattern);
            parameters.add(queryPattern);
        }
        return new QuerySpec(sql.toString(), parameters);
    }

    private record QuerySpec(String sql, List<Object> parameters) {
    }

    private TrendItem decode(String payload) {
        try { return mapper.readValue(payload, TrendItem.class); }
        catch (Exception e) { throw new IllegalStateException("趋势数据损坏", e); }
    }
}
