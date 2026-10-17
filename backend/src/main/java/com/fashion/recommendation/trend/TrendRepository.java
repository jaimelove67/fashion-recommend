package com.fashion.recommendation.trend;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TrendRepository {
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
        String fingerprint = TrendContentPolicy.reviewHash(item, mapper);
        int updated = jdbc.update("UPDATE trend_contents SET source_id=?,payload=?,published_at=?,fetched_at=?, "
                        + "moderation_status=CASE WHEN moderation_status='APPROVED' AND "
                        + "(reviewed_content_hash IS NULL OR reviewed_content_hash<>?) THEN 'PENDING_HUMAN' ELSE moderation_status END "
                        + "WHERE id=? AND fetched_at<=?",
                source, payload, Timestamp.from(item.publishedAt()), observedAt, fingerprint, item.id(), observedAt);
        if (updated == 0 && jdbc.queryForObject("SELECT COUNT(*) FROM trend_contents WHERE id=?", Integer.class, item.id()) == 0) {
            jdbc.update("INSERT INTO trend_contents(id,platform,source_id,payload,published_at,fetched_at,moderation_status) VALUES (?,?,?,?,?,?,'PENDING_HUMAN')",
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
        return querySince(cutoff, false);
    }

    public List<TrendItem> sinceIncludingHotBoards(Instant cutoff) {
        return querySince(cutoff, true);
    }

    private List<TrendItem> querySince(Instant cutoff, boolean includeHotBoards) {
        // Filter legacy board rows before the result limit so they cannot crowd out creator posts.
        String boardFilter = includeHotBoards ? "" : "AND payload NOT LIKE '%\"mediaType\":\"board\"%' ";
        return jdbc.query("SELECT payload,reviewed_content_hash FROM trend_contents WHERE fetched_at>=? "
                        + "AND moderation_status='APPROVED' AND hidden=FALSE AND reviewed_content_hash IS NOT NULL " + boardFilter
                        + "ORDER BY published_at DESC LIMIT 1000",
                (rs, row) -> decodeApproved(rs.getString(1), rs.getString(2)), Timestamp.from(cutoff))
                .stream().filter(Objects::nonNull).toList();
    }

    public Optional<TrendItem> find(String id) {
        return jdbc.query("SELECT payload,reviewed_content_hash FROM trend_contents WHERE id=? "
                        + "AND moderation_status='APPROVED' AND hidden=FALSE AND reviewed_content_hash IS NOT NULL",
                (rs, row) -> decodeApproved(rs.getString(1), rs.getString(2)), id)
                .stream().filter(Objects::nonNull).findFirst();
    }

    public List<TrendContentReview> pendingReviews(int limit) {
        return jdbc.query("SELECT payload,moderation_status,hidden FROM trend_contents "
                        + "WHERE moderation_status IN ('PENDING_HUMAN','PENDING_AI','AI_FAILED') AND hidden=FALSE "
                        + "ORDER BY published_at DESC LIMIT ?", (rs, row) -> reviewCandidate(
                                rs.getString(1), rs.getString(2), rs.getBoolean(3)), limit);
    }

    public Optional<TrendContentReview> reviewCandidate(String id) {
        return jdbc.query("SELECT payload,moderation_status,hidden FROM trend_contents WHERE id=?",
                (rs, row) -> reviewCandidate(rs.getString(1), rs.getString(2), rs.getBoolean(3)), id).stream().findFirst();
    }

    @Transactional
    public boolean review(String id, String fingerprint, boolean approved, String actor, String note) {
        var rows = jdbc.query("SELECT payload FROM trend_contents WHERE id=? FOR UPDATE",
                (rs, row) -> decode(rs.getString(1)), id);
        if (rows.isEmpty() || !TrendContentPolicy.reviewHash(rows.get(0), mapper).equals(fingerprint)) return false;
        if (approved && (!TrendContentPolicy.hasReviewableImage(rows.get(0))
                || TrendContentPolicy.exclusionReason(rows.get(0)) != null
                || TrendTopics.excludedShowOrCelebrity(rows.get(0)))) {
            throw new IllegalArgumentException("该内容不符合图文穿搭范围，不能通过复核");
        }
        jdbc.update("UPDATE trend_contents SET moderation_status=?,hidden=?,reviewed_content_hash=?,"
                        + "reviewed_by=?,reviewed_at=?,review_note=? WHERE id=?",
                approved ? "APPROVED" : "REJECTED", !approved, fingerprint, actor, Timestamp.from(Instant.now()), note, id);
        return true;
    }

    public Map<String, Integer> pendingCounts(Instant cutoff) {
        Map<String, Integer> counts = new HashMap<>();
        jdbc.query("SELECT source_id,COUNT(*) FROM trend_contents WHERE published_at>=? AND fetched_at>=? "
                        + "AND hidden=FALSE AND moderation_status IN ('PENDING_HUMAN','PENDING_AI','AI_FAILED') GROUP BY source_id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> counts.put(rs.getString(1), rs.getInt(2)),
                Timestamp.from(cutoff), Timestamp.from(cutoff));
        return counts;
    }

    private TrendContentReview reviewCandidate(String payload, String status, boolean hidden) {
        TrendItem item = decode(payload);
        return new TrendContentReview(item, TrendContentPolicy.reviewHash(item, mapper), status, hidden);
    }

    private TrendItem decodeApproved(String payload, String fingerprint) {
        TrendItem item = decode(payload);
        return TrendContentPolicy.reviewHash(item, mapper).equals(fingerprint) ? item : null;
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

    public void clearStatus(String id) {
        jdbc.update("DELETE FROM trend_source_status WHERE id=?", id);
    }

    private TrendItem decode(String payload) {
        try { return mapper.readValue(payload, TrendItem.class); }
        catch (Exception e) { throw new IllegalStateException("趋势数据损坏", e); }
    }
}
