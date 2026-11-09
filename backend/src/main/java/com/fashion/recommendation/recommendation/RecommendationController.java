package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {
    private final RecommendationService recommendationService;
    private final RecommendationVisualService recommendationVisualService;

    public RecommendationController(
            RecommendationService recommendationService,
            RecommendationVisualService recommendationVisualService) {
        this.recommendationService = recommendationService;
        this.recommendationVisualService = recommendationVisualService;
    }

    @PostMapping("/recommendations")
    public ApiResponse<Recommendation> generate(
            Principal principal,
            @Valid @RequestBody RecommendationRequest request) {
        return ApiResponse.ok(recommendationService.generate(principal.getName(), request));
    }

    @GetMapping("/recommendations/{recommendationId}")
    public ApiResponse<Recommendation> get(
            Principal principal,
            @PathVariable Long recommendationId) {
        return ApiResponse.ok(recommendationService.get(principal.getName(), recommendationId));
    }

    @GetMapping("/me/recommendations")
    public ApiResponse<RecommendationPage> list(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "") String query) {
        return ApiResponse.ok(recommendationService.list(principal.getName(), page, size, filter, query));
    }

    @PostMapping("/me/recommendations/{recommendationId}/visual")
    public ApiResponse<RecommendationVisualResponse> visual(
            Principal principal,
            @PathVariable Long recommendationId,
            @RequestParam(defaultValue = "false") boolean force,
            @Valid @RequestBody(required = false) VisualItemsRequest request) {
        if (request == null) return ApiResponse.ok(recommendationVisualService.generate(principal.getName(), recommendationId, force));
        return ApiResponse.ok(recommendationVisualService.generate(principal.getName(), recommendationId, force, request));
    }

    @GetMapping("/me/recommendations/visual-quota")
    public ApiResponse<RecommendationVisualRepository.Quota> visualQuota(Principal principal) {
        return ApiResponse.ok(recommendationVisualService.quota(principal.getName()));
    }

    @GetMapping("/me/recommendations/{recommendationId}/visual")
    public ApiResponse<RecommendationVisualResponse> currentVisual(Principal principal, @PathVariable Long recommendationId) {
        return ApiResponse.ok(recommendationVisualService.current(principal.getName(), recommendationId));
    }

    @GetMapping("/me/recommendations/{recommendationId}/visual/images/{assetId}")
    public ResponseEntity<byte[]> visualImage(Principal principal, @PathVariable Long recommendationId, @PathVariable String assetId) {
        var image = recommendationVisualService.image(principal.getName(), recommendationId, assetId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("X-Content-Type-Options", "nosniff").eTag("\"" + assetId + "\"").body(image.content());
    }

    @PostMapping("/me/recommendations/{recommendationId}/save")
    public ApiResponse<Recommendation> save(
            Principal principal,
            @PathVariable Long recommendationId) {
        return ApiResponse.ok(recommendationService.save(principal.getName(), recommendationId));
    }

    @PostMapping("/me/recommendations/{recommendationId}/feedback")
    public ApiResponse<Recommendation> feedback(
            Principal principal,
            @PathVariable Long recommendationId,
            @Valid @RequestBody RecommendationFeedbackRequest request) {
        return ApiResponse.ok(recommendationService.feedback(principal.getName(), recommendationId, request));
    }
}
