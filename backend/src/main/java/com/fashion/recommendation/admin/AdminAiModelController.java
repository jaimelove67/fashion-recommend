package com.fashion.recommendation.admin;

import com.fashion.recommendation.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai-models")
public class AdminAiModelController {
    private final AdminAiModelSettingsService settingsService;

    public AdminAiModelController(AdminAiModelSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ApiResponse<List<AdminAiModelConfigView>> list() {
        return ApiResponse.ok(settingsService.list());
    }

    @PutMapping("/{capability}")
    public ApiResponse<AdminAiModelConfigView> update(
            Principal principal,
            @PathVariable String capability,
            @Valid @RequestBody AdminAiModelConfigUpdateRequest request) {
        return ApiResponse.ok(settingsService.update(principal.getName(), capability, request));
    }

    @DeleteMapping("/{capability}")
    public ApiResponse<AdminAiModelConfigView> reset(Principal principal, @PathVariable String capability) {
        return ApiResponse.ok(settingsService.reset(principal.getName(), capability));
    }
}
