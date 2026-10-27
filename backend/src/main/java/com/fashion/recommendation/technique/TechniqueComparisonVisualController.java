package com.fashion.recommendation.technique;

import com.fashion.recommendation.admin.AdminAuditRepository;
import com.fashion.recommendation.common.ApiResponse;
import java.security.Principal;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TechniqueComparisonVisualController {
    private final TechniqueComparisonVisualService service;
    private final TechniqueComparisonVisualRepository repository;
    private final AdminAuditRepository audit;

    public TechniqueComparisonVisualController(TechniqueComparisonVisualService service,
            TechniqueComparisonVisualRepository repository, AdminAuditRepository audit) {
        this.service = service;
        this.repository = repository;
        this.audit = audit;
    }

    @PostMapping("/api/v1/admin/outfit-techniques/comparison-visuals")
    public ApiResponse<StartResult> start(Principal principal) {
        boolean accepted = service.start();
        audit.append(principal.getName(), "TECHNIQUE_COMPARISON", "outfit_technique", "all",
                accepted ? "QUEUED" : "SKIPPED", "补充原文对照图或有月度限额的AI示意图");
        return ApiResponse.ok(new StartResult(accepted, service.budget()));
    }

    @GetMapping("/api/v1/admin/outfit-techniques/comparison-visuals/status")
    public ApiResponse<TechniqueComparisonVisualRepository.Budget> status() { return ApiResponse.ok(service.budget()); }

    @GetMapping("/api/v1/outfit-techniques/comparison-images/{assetId}")
    public ResponseEntity<byte[]> image(@PathVariable String assetId) {
        var image = repository.image(assetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff").body(image.content());
    }

    public record StartResult(boolean accepted, TechniqueComparisonVisualRepository.Budget budget) {}
}
