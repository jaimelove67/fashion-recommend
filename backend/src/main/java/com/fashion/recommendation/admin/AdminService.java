package com.fashion.recommendation.admin;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    public static final long MAX_PAGE_OFFSET = 1_000_000L;
    private static final int MAX_QUERY_LENGTH = 32;
    private static final int MAX_FEEDBACK_QUERY_LENGTH = 64;
    private static final Set<String> FEEDBACK_STATUSES = Set.of("PENDING", "REVIEWED", "RESOLVED");

    private final AdminRepository adminRepository;
    private final AdminFeedbackRepository feedbackRepository;
    private final AdminAuditRepository auditRepository;

    public AdminService(
            AdminRepository adminRepository,
            AdminFeedbackRepository feedbackRepository,
            AdminAuditRepository auditRepository) {
        this.adminRepository = adminRepository;
        this.feedbackRepository = feedbackRepository;
        this.auditRepository = auditRepository;
    }

    public AdminOverview overview() {
        return adminRepository.overview();
    }

    public AdminUserPage listUsers(int page, int size, String query) {
        long offset = (long) page * size;
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || offset > MAX_PAGE_OFFSET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法");
        }

        String normalizedQuery = normalizeQuery(query);
        long total = adminRepository.countUsers(toLikePattern(normalizedQuery));
        var items = adminRepository.findUsers(toLikePattern(normalizedQuery), size, offset);
        boolean hasNext = offset + size < total;
        return new AdminUserPage(items, total, page, size, hasNext);
    }

    @Transactional
    public AdminUser updateStatus(String actor, String username, Boolean enabled) {
        if (enabled == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled 参数不合法");
        }
        AdminUser before = adminRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (!enabled && actor.equals(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能停用当前管理员账号");
        }
        if (before.enabled() == enabled) {
            return before;
        }
        if (!adminRepository.updateEnabled(username, enabled)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        AdminUser updated = adminRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        auditRepository.append(
                actor,
                "USER_STATUS_UPDATE",
                "USER",
                username,
                "SUCCESS",
                "账号状态：" + statusLabel(before.enabled()) + " → " + statusLabel(updated.enabled()));
        return updated;
    }

    public AdminFeedbackPage listFeedback(int page, int size, String status, String query) {
        validatePage(page, size);
        String normalizedStatus = normalizeFeedbackStatus(status);
        String normalizedQuery = normalizeFeedbackQuery(query);
        String queryPattern = toLikePattern(normalizedQuery);
        long offset = (long) page * size;
        long total = feedbackRepository.count(normalizedStatus, queryPattern);
        List<AdminFeedback> items = feedbackRepository.findPage(normalizedStatus, queryPattern, size, offset);
        return new AdminFeedbackPage(items, total, page, size, offset + size < total);
    }

    public AdminFeedback updateFeedbackStatus(
            String actor,
            long recommendationId,
            String requestedStatus) {
        String status = normalizeFeedbackStatus(requestedStatus);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "反馈状态不合法");
        }
        AdminFeedback before = feedbackRepository.findByRecommendationId(recommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "反馈不存在"));
        Instant handledAt = "PENDING".equals(status) ? null : Instant.now();
        String handledBy = handledAt == null ? null : actor;
        if (!feedbackRepository.updateStatus(recommendationId, status, handledBy, handledAt)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "反馈不存在");
        }
        AdminFeedback updated = feedbackRepository.findByRecommendationId(recommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "反馈不存在"));
        auditRepository.append(
                actor,
                "FEEDBACK_STATUS_UPDATE",
                "FEEDBACK",
                String.valueOf(recommendationId),
                "SUCCESS",
                "反馈状态：" + before.moderationStatus() + " → " + updated.moderationStatus());
        return updated;
    }

    public AdminAuditLogPage listAuditLogs(int page, int size, String action, String outcome) {
        validatePage(page, size);
        String normalizedAction = normalizeFilter(action, 64, "操作类型");
        String normalizedOutcome = normalizeFilter(outcome, 24, "操作结果");
        long offset = (long) page * size;
        long total = auditRepository.count(normalizedAction, normalizedOutcome);
        List<AdminAuditLog> items = auditRepository.findPage(normalizedAction, normalizedOutcome, size, offset);
        return new AdminAuditLogPage(items, total, page, size, offset + size < total);
    }

    private static String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询条件不能超过 32 个字符");
        }
        return normalized.isEmpty() ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeFeedbackQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.trim();
        if (normalized.length() > MAX_FEEDBACK_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "反馈查询条件不能超过 64 个字符");
        }
        return normalized.isEmpty() ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeFeedbackStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!FEEDBACK_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "反馈状态不合法");
        }
        return normalized;
    }

    private static String normalizeFilter(String value, int maxLength, String label) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static void validatePage(int page, int size) {
        long offset = (long) page * size;
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || offset > MAX_PAGE_OFFSET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法");
        }
    }

    private static String statusLabel(boolean enabled) {
        return enabled ? "启用" : "停用";
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
}
