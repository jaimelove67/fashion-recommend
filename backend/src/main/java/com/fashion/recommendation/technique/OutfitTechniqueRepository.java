package com.fashion.recommendation.technique;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class OutfitTechniqueRepository {
    static final Duration PUBLICATION_INTERVAL = Duration.ofDays(7);
    static final Duration RETRY_DELAY = Duration.ofHours(48);
    static final Duration LEASE_DURATION = Duration.ofMinutes(15);
    static final int WEEKLY_ATTEMPT_LIMIT = 2;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public OutfitTechniqueRepository(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public List<OutfitTechnique> latest() {
        return jdbc.query("SELECT payload FROM outfit_techniques ORDER BY published_at DESC,id DESC LIMIT 24",
                (rs, row) -> decode(rs.getString(1)));
    }

    public List<String> recentTitles() {
        return latest().stream().map(OutfitTechnique::title).toList();
    }

    /** Reads never claim a lease, reset a timer, or start an external request. */
    public ScheduleState state(Instant now) {
        return state(readSchedule(false), now);
    }

    public Optional<String> tryAcquire(Instant now) {
        return transactions.execute(ignored -> {
            Schedule schedule = readSchedule(true);
            if (schedule.leaseToken() != null) {
                if (schedule.leaseUntil() != null && schedule.leaseUntil().isAfter(now)) return Optional.empty();
                // A crashed attempt still consumes its durable weekly budget.
                jdbc.update("UPDATE outfit_technique_attempts SET finished_at=?,outcome='FAILED',error_code='lease-expired' "
                        + "WHERE id=? AND finished_at IS NULL", timestamp(now), schedule.leaseToken());
                jdbc.update("UPDATE outfit_technique_schedule SET lease_token=NULL,lease_until=NULL,last_error_code='lease-expired' "
                        + "WHERE id=1 AND lease_token=?", schedule.leaseToken());
                schedule = readSchedule(false);
            }
            ScheduleState state = state(schedule, now);
            if (state.nextDueAt() != null && state.nextDueAt().isAfter(now)) return Optional.empty();
            String token = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO outfit_technique_attempts(id,started_at,outcome) VALUES (?,?,'COLLECTING')", token, timestamp(now));
            jdbc.update("UPDATE outfit_technique_schedule SET last_attempt_at=?,lease_token=?,lease_until=? WHERE id=1",
                    timestamp(now), token, timestamp(now.plus(LEASE_DURATION)));
            return Optional.of(token);
        });
    }

    /** Publication and lease release commit together; a stale worker cannot overwrite its successor. */
    public boolean publish(String token, TechniqueGenerationResult result, Instant now) {
        return Boolean.TRUE.equals(transactions.execute(ignored -> {
            Schedule schedule = readSchedule(true);
            if (!ownsLiveLease(schedule, token, now)) return false;
            OutfitTechnique generated = result == null ? null : result.technique();
            if (generated == null || result.qualityScore() < 85 || generated.sources() == null
                    || generated.sources().stream().map(OutfitTechnique.Source::url).distinct().count() < 2
                    || generated.id() == null || !generated.id().matches("[A-Za-z0-9:_-]{1,160}")
                    || generated.title() == null || generated.title().isBlank() || generated.title().length() > 200) {
                failLocked(token, "quality-rejected", now, result);
                return false;
            }
            String normalizedTitle = normalizeTitle(generated.title());
            String sourcesHash;
            try { sourcesHash = sourceSetHash(generated.sources()); }
            catch (IllegalArgumentException exception) {
                failLocked(token, "quality-rejected", now, result);
                return false;
            }
            if (normalizedTitle.isEmpty() || normalizedTitle.length() > 240 || jdbc.queryForObject(
                    "SELECT COUNT(*) FROM outfit_techniques WHERE id=? OR normalized_title=? OR source_set_hash=?",
                    Long.class, generated.id(), normalizedTitle, sourcesHash) > 0) {
                failLocked(token, "duplicate-content", now, result);
                return false;
            }
            // Check persisted publications as well as the singleton state before committing.
            if (jdbc.queryForObject("SELECT COUNT(*) FROM outfit_techniques WHERE published_at>?", Long.class,
                    timestamp(now.minus(PUBLICATION_INTERVAL))) > 0) {
                failLocked(token, "publication-not-due", now, result);
                return false;
            }
            OutfitTechnique publication = new OutfitTechnique(generated.id(), generated.category(), generated.title(),
                    generated.summary(), generated.sources(), generated.sourceNotes(), generated.synthesisNote(), generated.steps(),
                    generated.reason(), generated.example(), generated.suitable(), generated.limit(), generated.check(), now,
                    generated.generationMode(), generated.modelName());
            jdbc.update("INSERT INTO outfit_techniques(id,normalized_title,source_set_hash,payload,published_at) VALUES (?,?,?,?,?)",
                    publication.id(), normalizedTitle, sourcesHash, encode(publication), timestamp(now));
            finishAttempt(token, "PUBLISHED", null, now, result);
            jdbc.update("UPDATE outfit_technique_schedule SET last_success_at=?,last_error_code=NULL,lease_token=NULL,lease_until=NULL "
                    + "WHERE id=1 AND lease_token=?", timestamp(now), token);
            return true;
        }));
    }

    public void fail(String token, String errorCode, Instant now) {
        transactions.executeWithoutResult(ignored -> {
            Schedule schedule = readSchedule(true);
            if (token.equals(schedule.leaseToken())) failLocked(token, safeErrorCode(errorCode), now, null);
        });
    }

    private void failLocked(String token, String errorCode, Instant now, TechniqueGenerationResult result) {
        finishAttempt(token, "FAILED", errorCode, now, result);
        jdbc.update("UPDATE outfit_technique_schedule SET last_error_code=?,lease_token=NULL,lease_until=NULL WHERE id=1 AND lease_token=?",
                errorCode, token);
    }

    private void finishAttempt(String token, String outcome, String errorCode, Instant now, TechniqueGenerationResult result) {
        jdbc.update("UPDATE outfit_technique_attempts SET finished_at=?,outcome=?,error_code=?,quality_score=?,model_name=?,provider_call_id=? "
                        + "WHERE id=? AND finished_at IS NULL", timestamp(now), outcome, errorCode,
                result == null ? null : result.qualityScore(),
                result == null || result.technique() == null ? null : bounded(result.technique().modelName(), 160),
                result == null ? null : bounded(result.providerCallId(), 256), token);
    }

    private ScheduleState state(Schedule schedule, Instant now) {
        List<Instant> attempts = jdbc.query("SELECT started_at FROM outfit_technique_attempts WHERE started_at>? ORDER BY started_at",
                (rs, row) -> instant(rs, "started_at"), timestamp(now.minus(PUBLICATION_INTERVAL)));
        Instant due = schedule.lastSuccessAt() == null ? now : schedule.lastSuccessAt().plus(PUBLICATION_INTERVAL);
        if (schedule.lastAttemptAt() != null) due = later(due, schedule.lastAttemptAt().plus(RETRY_DELAY));
        if (attempts.size() >= WEEKLY_ATTEMPT_LIMIT) due = later(due, attempts.get(attempts.size() - WEEKLY_ATTEMPT_LIMIT).plus(PUBLICATION_INTERVAL));
        boolean collecting = schedule.leaseToken() != null && schedule.leaseUntil() != null && schedule.leaseUntil().isAfter(now);
        if (collecting) due = later(due, schedule.leaseUntil());
        String error = schedule.leaseToken() != null && !collecting ? "lease-expired" : schedule.lastErrorCode();
        return new ScheduleState(schedule.lastAttemptAt(), schedule.lastSuccessAt(), due, error, attempts.size(), collecting);
    }

    private Schedule readSchedule(boolean lock) {
        return jdbc.queryForObject("SELECT last_attempt_at,last_success_at,last_error_code,lease_token,lease_until "
                        + "FROM outfit_technique_schedule WHERE id=1" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new Schedule(instant(rs, "last_attempt_at"), instant(rs, "last_success_at"),
                        rs.getString("last_error_code"), rs.getString("lease_token"), instant(rs, "lease_until")));
    }

    private static boolean ownsLiveLease(Schedule state, String token, Instant now) {
        return token.equals(state.leaseToken()) && state.leaseUntil() != null && state.leaseUntil().isAfter(now);
    }

    static String normalizeTitle(String title) {
        return Normalizer.normalize(title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }

    private static String sourceSetHash(List<OutfitTechnique.Source> sources) {
        List<String> urls = sources.stream().map(OutfitTechnique.Source::url).map(url -> {
            if (url == null) throw new IllegalArgumentException("invalid source");
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException("invalid source");
            String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
            return uri.getHost().toLowerCase(Locale.ROOT) + (uri.getPort() < 0 ? "" : ":" + uri.getPort())
                    + path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        }).distinct().sorted().toList();
        if (urls.size() < 2) throw new IllegalArgumentException("insufficient sources");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n", urls).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String safeErrorCode(String value) {
        return value != null && List.of("source-unavailable", "insufficient-sources", "generation-unavailable", "quality-rejected", "internal-failure").contains(value)
                ? value : "internal-failure";
    }

    private String encode(OutfitTechnique value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Technique payload could not be serialized", exception); }
    }

    private OutfitTechnique decode(String value) {
        try { return mapper.readValue(value, OutfitTechnique.class); }
        catch (Exception exception) { throw new IllegalStateException("Technique payload could not be read", exception); }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) { return Timestamp.from(value); }
    private static Instant later(Instant first, Instant second) { return first.isAfter(second) ? first : second; }
    private static String bounded(String value, int length) { return value == null ? null : value.substring(0, Math.min(length, value.length())); }

    private record Schedule(Instant lastAttemptAt, Instant lastSuccessAt, String lastErrorCode, String leaseToken, Instant leaseUntil) {}
    public record ScheduleState(Instant lastAttemptAt, Instant lastSuccessAt, Instant nextDueAt, String lastErrorCode,
            int attemptsInWindow, boolean collecting) {}
}
