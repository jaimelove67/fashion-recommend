package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {
    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
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
    public ApiResponse<List<Recommendation>> list(Principal principal) {
        return ApiResponse.ok(recommendationService.list(principal.getName()));
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
