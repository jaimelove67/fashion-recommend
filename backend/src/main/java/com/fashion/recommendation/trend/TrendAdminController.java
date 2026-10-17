package com.fashion.recommendation.trend;

import com.fashion.recommendation.common.ApiResponse;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/trends")
public class TrendAdminController {
    private final TrendService service;
    private final com.fashion.recommendation.admin.AdminAuditRepository audit;
    private final TrendRepository repository;

    public TrendAdminController(
            TrendService service,
            com.fashion.recommendation.admin.AdminAuditRepository audit, TrendRepository repository) {
        this.service = service;
        this.audit = audit;
        this.repository = repository;
    }

    @GetMapping("/review-queue")
    public ApiResponse<List<TrendContentReview>> reviewQueue(@RequestParam(defaultValue = "20") int limit) {
        if (limit < 1 || limit > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "复核条数应为1到50");
        return ApiResponse.ok(repository.pendingReviews(limit));
    }

    @PostMapping("/{id}/content-review")
    @Transactional
    public ApiResponse<TrendContentReview> review(@PathVariable String id, @RequestBody ContentReviewRequest request,
            Principal principal) {
        if (request.approved() == null || request.fingerprint() == null
                || !request.fingerprint().matches("[a-f0-9]{64}") || request.note() == null
                || request.note().isBlank() || request.note().length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提交当前内容版本和图片复核说明");
        }
        if (repository.reviewCandidate(id).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "趋势内容不存在");
        try {
            if (!repository.review(id, request.fingerprint(), request.approved(), principal.getName(), request.note().trim()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "内容已变化，请重新检查图片后提交");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        audit.append(principal.getName(), "TREND_CONTENT_REVIEW", "trend_content", id, "SUCCESS",
                (request.approved() ? "通过：" : "拒绝：") + request.note().trim());
        return ApiResponse.ok(repository.reviewCandidate(id).orElseThrow());
    }

    public record ContentReviewRequest(Boolean approved, String fingerprint, String note) {}

    @PostMapping("/refresh")
    public ApiResponse<TrendFeed> refresh(Principal principal) {
        service.refresh();
        audit.append(
                principal.getName(),
                "TREND_REFRESH",
                "trend_source",
                "all",
                "SUCCESS",
                "刷新趋势来源，结果见来源状态");
        return ApiResponse.ok(service.currentFeed(null, null));
    }
}
