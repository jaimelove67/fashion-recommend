package com.fashion.recommendation.technique;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.storage.StoredImageData;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class TechniqueComparisonVisualRepository {
    public static final int MONTHLY_LIMIT = 10;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public TechniqueComparisonVisualRepository(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        transactions = new TransactionTemplate(manager);
    }

    public OutfitTechnique decorate(OutfitTechnique item) {
        if (item.comparisonVisual() != null) return item;
        var visual = jdbc.query("SELECT payload FROM technique_comparison_visuals WHERE technique_id=? AND fingerprint=?",
                (rs, row) -> decode(rs.getString(1)), item.id(), fingerprint(item));
        return visual.isEmpty() ? item : item.withComparisonVisual(visual.get(0));
    }

    /** Reservation commits before any provider call. Crashes, timeouts and failures never refund it. */
    public Optional<String> reserve(OutfitTechnique item, Instant now) {
        return transactions.execute(ignored -> {
            lock();
            String month = month(now);
            if (decorate(item).comparisonVisual() != null || jdbc.queryForObject(
                    "SELECT COUNT(*) FROM technique_visual_attempts WHERE month_key=?", Integer.class, month) >= MONTHLY_LIMIT
                    || jdbc.queryForObject("SELECT COUNT(*) FROM technique_visual_attempts WHERE month_key=? AND technique_id=?",
                            Integer.class, month, item.id()) > 0) return Optional.empty();
            String token = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO technique_visual_attempts(id,month_key,technique_id,fingerprint,started_at,outcome) VALUES (?,?,?,?,?,'RESERVED')",
                    token, month, item.id(), fingerprint(item), Timestamp.from(now));
            return Optional.of(token);
        });
    }

    public void source(OutfitTechnique item, OutfitTechnique.ComparisonVisual visual) {
        transactions.executeWithoutResult(ignored -> { lock(); save(item, visual, null, null); });
    }

    public void finish(String token, OutfitTechnique item, StoredImageData data, Instant now) {
        transactions.executeWithoutResult(ignored -> {
            lock();
            int changed = jdbc.update("UPDATE technique_visual_attempts SET finished_at=?,outcome=? WHERE id=? AND technique_id=? AND fingerprint=? AND outcome='RESERVED'",
                    Timestamp.from(now), data == null ? "FAILED" : "SUCCEEDED", token, item.id(), fingerprint(item));
            if (changed == 0 || data == null) return;
            // An original discovered by another worker always wins over an AI illustration.
            var current = decorate(item).comparisonVisual();
            if (current != null && "source".equals(current.kind())) return;
            var visual = new OutfitTechnique.ComparisonVisual("generated", List.of(
                    new OutfitTechnique.ComparisonVisual.Image("/api/v1/outfit-techniques/comparison-images/" + token,
                            "AI示意：调整前与调整后的穿搭对比")), null,
                    "AI生成示意图，用于理解假设搭配调整；不是原帖照片，也不代表真实穿着效果。");
            save(item, visual, token, data);
        });
    }

    public Optional<StoredImageData> image(String assetId) {
        if (assetId == null || !assetId.matches("[a-f0-9-]{36}")) return Optional.empty();
        return jdbc.query("SELECT image_content,content_type FROM technique_comparison_visuals WHERE asset_id=? AND image_content IS NOT NULL",
                (rs, row) -> new StoredImageData(rs.getBytes(1), rs.getString(2)), assetId).stream().findFirst();
    }

    public Budget budget(Instant now) {
        String month = month(now);
        return new Budget(month, jdbc.queryForObject("SELECT COUNT(*) FROM technique_visual_attempts WHERE month_key=?",
                Integer.class, month), MONTHLY_LIMIT);
    }

    private void lock() { jdbc.queryForObject("SELECT id FROM technique_visual_budget WHERE id=1 FOR UPDATE", Integer.class); }

    private void save(OutfitTechnique item, OutfitTechnique.ComparisonVisual visual, String token, StoredImageData data) {
        String payload;
        try { payload = mapper.writeValueAsString(visual); }
        catch (Exception exception) { throw new IllegalStateException("Comparison visual could not be stored", exception); }
        byte[] bytes = data == null ? null : data.content();
        String type = data == null ? null : data.contentType();
        // Content versions coexist so a slow worker can never overwrite a newer lesson's visual.
        if (jdbc.update("UPDATE technique_comparison_visuals SET payload=?,asset_id=?,image_content=?,content_type=? WHERE technique_id=? AND fingerprint=?",
                payload, token, bytes, type, item.id(), fingerprint(item)) == 0) {
            jdbc.update("INSERT INTO technique_comparison_visuals(technique_id,fingerprint,payload,asset_id,image_content,content_type) VALUES (?,?,?,?,?,?)",
                    item.id(), fingerprint(item), payload, token, bytes, type);
        }
    }

    private OutfitTechnique.ComparisonVisual decode(String payload) {
        try { return mapper.readValue(payload, OutfitTechnique.ComparisonVisual.class); }
        catch (Exception exception) { throw new IllegalStateException("Comparison visual could not be read", exception); }
    }

    private static String fingerprint(OutfitTechnique item) {
        try {
            String text = item.title() + "\n" + (item.example() == null ? "" : item.example().before() + "\n" + item.example().after());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static String month(Instant now) { return YearMonth.from(now.atZone(ZoneId.of("Asia/Shanghai"))).toString(); }
    public record Budget(String month, int used, int limit) {}
}
