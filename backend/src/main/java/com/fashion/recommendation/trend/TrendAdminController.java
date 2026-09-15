package com.fashion.recommendation.trend;

import com.fashion.recommendation.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/trends")
public class TrendAdminController {
    private final TrendService service;
    private final TrendModerationService moderationService;
    private final com.fashion.recommendation.admin.AdminAuditRepository audit;
    public TrendAdminController(TrendService service,
            TrendModerationService moderationService,
            com.fashion.recommendation.admin.AdminAuditRepository audit) {
        this.service = service; this.moderationService = moderationService; this.audit = audit;
    }
    @PostMapping("/refresh")
    public ApiResponse<TrendFeed> refresh(Principal principal) {
        service.refresh();
        audit.append(principal.getName(), "TREND_REFRESH", "trend_source", "all", "SUCCESS", "刷新趋势来源，结果见来源状态");
        return ApiResponse.ok(service.currentFeed(null, null));
    }

    @PostMapping("/ai-review")
    public ApiResponse<TrendModerationRun> aiReview(
            @RequestParam(defaultValue = "10") int limit,
            Principal principal) {
        TrendModerationRun run = moderationService.processPending(limit);
        audit.append(
                principal.getName(),
                "TREND_AI_REVIEW",
                "trend",
                "batch",
                "SUCCESS",
                "请求 " + run.requested() + " 条，完成 " + run.reviewed() + " 条，失败 " + run.failed() + " 条");
        return ApiResponse.ok(run);
    }

    @GetMapping("/contents")
    public ApiResponse<TrendModerationPage> contents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query) {
        return ApiResponse.ok(moderationService.list(page, size, status, query));
    }

    @PostMapping("/contents/{id}/retry-ai")
    public ApiResponse<TrendModerationItem> retryAi(@PathVariable String id, Principal principal) {
        return ApiResponse.ok(moderationService.retryAi(principal.getName(), id));
    }

    @PutMapping("/contents/{id}/review")
    public ApiResponse<TrendModerationItem> review(
            @PathVariable String id,
            @Valid @RequestBody TrendHumanReviewRequest request,
            Principal principal) {
        return ApiResponse.ok(moderationService.finalizeHuman(
                principal.getName(), id, request.status(), request.note()));
    }

    @PutMapping("/{id}/visibility")
    public ApiResponse<TrendModerationItem> visibility(
            @PathVariable String id,
            @RequestBody Visibility body,
            Principal principal) {
        return ApiResponse.ok(moderationService.updateVisibility(principal.getName(), id, body.hidden()));
    }
    public record Visibility(boolean hidden) {}
}
