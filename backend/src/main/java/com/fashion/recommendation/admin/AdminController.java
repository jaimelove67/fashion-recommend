package com.fashion.recommendation.admin;

import com.fashion.recommendation.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminOverview> overview() {
        return ApiResponse.ok(adminService.overview());
    }

    @GetMapping("/users")
    public ApiResponse<AdminUserPage> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query) {
        return ApiResponse.ok(adminService.listUsers(page, size, query));
    }

    @PutMapping("/users/{username}/status")
    public ApiResponse<AdminUser> updateStatus(
            Principal principal,
            @PathVariable String username,
            @Valid @RequestBody AdminUserStatusRequest request) {
        return ApiResponse.ok(adminService.updateStatus(principal.getName(), username, request.enabled()));
    }

    @GetMapping("/feedback")
    public ApiResponse<AdminFeedbackPage> feedback(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query) {
        return ApiResponse.ok(adminService.listFeedback(page, size, status, query));
    }

    @PutMapping("/feedback/{recommendationId}/status")
    public ApiResponse<AdminFeedback> updateFeedbackStatus(
            Principal principal,
            @PathVariable long recommendationId,
            @Valid @RequestBody AdminFeedbackStatusRequest request) {
        return ApiResponse.ok(adminService.updateFeedbackStatus(
                principal.getName(), recommendationId, request.status()));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<AdminAuditLogPage> auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome) {
        return ApiResponse.ok(adminService.listAuditLogs(page, size, action, outcome));
    }
}
