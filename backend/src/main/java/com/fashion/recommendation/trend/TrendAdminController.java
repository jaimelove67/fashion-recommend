package com.fashion.recommendation.trend;

import com.fashion.recommendation.common.ApiResponse;
import java.security.Principal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/trends")
public class TrendAdminController {
    private final TrendService service;
    private final com.fashion.recommendation.admin.AdminAuditRepository audit;

    public TrendAdminController(
            TrendService service,
            com.fashion.recommendation.admin.AdminAuditRepository audit) {
        this.service = service;
        this.audit = audit;
    }

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
