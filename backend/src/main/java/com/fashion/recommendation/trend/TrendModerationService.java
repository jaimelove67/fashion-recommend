package com.fashion.recommendation.trend;

import com.fashion.recommendation.admin.AdminAuditRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lightweight business workflow for trend moderation.
 * AI proposes a decision; only a human review can publish or reject content.
 */
@Service
public class TrendModerationService {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    public static final long MAX_PAGE_OFFSET = 1_000_000L;
    private static final int MAX_BATCH_SIZE = 50;
    private static final int MAX_QUERY_LENGTH = 64;
    private static final Set<String> FILTER_STATUSES = Set.of(
            TrendModerationStatus.PENDING_AI,
            TrendModerationStatus.AI_FAILED,
            TrendModerationStatus.PENDING_HUMAN,
            TrendModerationStatus.APPROVED,
            TrendModerationStatus.REJECTED);
    private static final Set<String> HUMAN_STATUSES = Set.of(
            TrendModerationStatus.APPROVED,
            TrendModerationStatus.REJECTED);

    private final TrendRepository repository;
    private final TrendModerationClient client;
    private final AdminAuditRepository auditRepository;
    private final int batchSize;

    public TrendModerationService(
            TrendRepository repository,
            TrendModerationClient client,
            AdminAuditRepository auditRepository,
            @Value("${app.trends.ai-review-batch-size:10}") int batchSize) {
        this.repository = repository;
        this.client = client;
        this.auditRepository = auditRepository;
        this.batchSize = batchSize;
    }

    @Scheduled(
            initialDelayString = "${app.trends.ai-review-initial-delay:30000}",
            fixedDelayString = "${app.trends.ai-review-interval:300000}")
    public synchronized void scheduledReview() {
        if (client.enabled()) {
            processPending(batchSize);
        }
    }

    public synchronized TrendModerationRun processPending(int requestedLimit) {
        int limit = normalizeBatchSize(requestedLimit);
        if (!client.enabled()) {
            return summary(limit, 0, 0);
        }

        int reviewed = 0;
        int failed = 0;
        List<TrendModerationItem> items = repository.pendingAi(limit);
        for (TrendModerationItem item : items) {
            TrendAiReviewResult result;
            try {
                result = client.review(item.asTrendItem());
            } catch (TrendModerationException exception) {
                if (repository.markAiFailed(item.id(), exception.reason(), Instant.now())) {
                    failed++;
                }
                continue;
            } catch (RuntimeException exception) {
                // Do not persist provider messages or stack traces; keep a stable reason for operators.
                if (repository.markAiFailed(
                        item.id(), TrendModerationFailureReason.REQUEST_FAILED, Instant.now())) {
                    failed++;
                }
                continue;
            }
            if (repository.markAiReviewed(item.id(), result, Instant.now())) {
                reviewed++;
            }
        }
        return summary(limit, reviewed, failed);
    }

    public TrendModerationPage list(int page, int size, String status, String query) {
        validatePage(page, size);
        String normalizedStatus = normalizeFilterStatus(status);
        String normalizedQuery = normalizeQuery(query);
        String queryPattern = toLikePattern(normalizedQuery);
        long offset = (long) page * size;
        long total = repository.countModeration(normalizedStatus, queryPattern);
        List<TrendModerationItem> items = repository.findModerationPage(
                normalizedStatus, queryPattern, size, offset);
        return new TrendModerationPage(items, total, page, size, offset + size < total);
    }

    @Transactional
    public TrendModerationItem retryAi(String actor, String id) {
        TrendModerationItem before = findOrNotFound(id);
        if (!TrendModerationStatus.AI_FAILED.equals(before.moderationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有 AI 失败内容可以重试");
        }
        if (!repository.retryAi(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "内容状态已被其他操作更新，请刷新后重试");
        }
        auditRepository.append(actor, "TREND_AI_RETRY", "TREND", id, "SUCCESS", "重新进入 AI 初审队列");
        return findOrNotFound(id);
    }

    @Transactional
    public TrendModerationItem finalizeHuman(
            String actor,
            String id,
            String requestedStatus,
            String note) {
        String status = normalizeHumanStatus(requestedStatus);
        String normalizedNote = normalizeNote(note);
        TrendModerationItem before = findOrNotFound(id);
        if (!Set.of(TrendModerationStatus.PENDING_HUMAN, TrendModerationStatus.AI_FAILED)
                .contains(before.moderationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前内容不在人工终审队列");
        }
        if (TrendModerationStatus.AI_FAILED.equals(before.moderationStatus())
                && normalizedNote == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI 失败时人工处理必须填写说明");
        }
        if (!repository.finalizeHuman(id, status, actor, Instant.now(), normalizedNote)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "内容状态已被其他操作更新，请刷新后重试");
        }
        auditRepository.append(
                actor,
                "TREND_HUMAN_REVIEW",
                "TREND",
                id,
                "SUCCESS",
                "AI 建议=" + safe(before.aiDecision()) + "，人工决定=" + status
                        + (normalizedNote == null ? "" : "，说明=" + normalizedNote));
        return findOrNotFound(id);
    }

    @Transactional
    public TrendModerationItem updateVisibility(String actor, String id, boolean hidden) {
        findOrNotFound(id);
        if (!repository.updateVisibility(id, hidden)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "内容不存在");
        }
        auditRepository.append(
                actor,
                "TREND_VISIBILITY",
                "TREND",
                id,
                "SUCCESS",
                hidden ? "下架趋势内容" : "恢复趋势内容（不改变人工审核结论）");
        return findOrNotFound(id);
    }

    private TrendModerationRun summary(int requested, int reviewed, int failed) {
        return new TrendModerationRun(
                requested,
                reviewed,
                failed,
                repository.countByModerationStatus(TrendModerationStatus.PENDING_AI),
                repository.countByModerationStatus(TrendModerationStatus.PENDING_HUMAN),
                repository.countByModerationStatus(TrendModerationStatus.AI_FAILED),
                client.enabled());
    }

    private TrendModerationItem findOrNotFound(String id) {
        return repository.findModeration(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "趋势内容不存在"));
    }

    private static String normalizeFilterStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!FILTER_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "趋势审核状态不合法");
        }
        return normalized;
    }

    private static String normalizeHumanStatus(String status) {
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "人工审核状态不能为空");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!HUMAN_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "人工审核状态只能为 APPROVED 或 REJECTED");
        }
        return normalized;
    }

    private static String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "趋势内容查询条件不能超过 64 个字符");
        }
        return normalized.isEmpty() ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String normalized = note.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void validatePage(int page, int size) {
        long offset = (long) page * size;
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || offset > MAX_PAGE_OFFSET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法");
        }
    }

    private static int normalizeBatchSize(int requested) {
        return Math.max(1, Math.min(requested, MAX_BATCH_SIZE));
    }

    private static String toLikePattern(String query) {
        if (query == null) {
            return null;
        }
        String escaped = query.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String safe(String value) {
        return value == null ? "无" : value;
    }
}
