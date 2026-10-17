package com.fashion.recommendation.technique;

import com.fashion.recommendation.admin.AdminAuditRepository;
import com.fashion.recommendation.common.ApiResponse;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/outfit-techniques")
public class OutfitTechniqueAdminController {
    private final WeeklyTechniqueService service;
    private final AdminAuditRepository audit;

    public OutfitTechniqueAdminController(WeeklyTechniqueService service, AdminAuditRepository audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/status")
    public ApiResponse<WeeklyTechniqueService.OperationalStatus> status() { return ApiResponse.ok(service.status()); }

    @PostMapping("/refresh")
    public ApiResponse<WeeklyTechniqueService.RefreshResult> refresh(Principal principal) {
        var result = service.refresh();
        audit.append(principal.getName(), "TECHNIQUE_REFRESH", "outfit_technique", "weekly",
                result.attempted() ? "ATTEMPTED" : "SKIPPED", result.outcome());
        return ApiResponse.ok(result);
    }
}
