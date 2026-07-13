package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
            @RequestHeader(value = "X-User-Id", defaultValue = "demo-user") String userId,
            @Valid @RequestBody RecommendationRequest request) {
        return ApiResponse.ok(recommendationService.generate(userId, request));
    }

    @GetMapping("/recommendations/{recommendationId}")
    public ApiResponse<Recommendation> get(
            @RequestHeader(value = "X-User-Id", defaultValue = "demo-user") String userId,
            @PathVariable Long recommendationId) {
        return ApiResponse.ok(recommendationService.get(userId, recommendationId));
    }

    @GetMapping("/me/recommendations")
    public ApiResponse<List<Recommendation>> list(
            @RequestHeader(value = "X-User-Id", defaultValue = "demo-user") String userId) {
        return ApiResponse.ok(recommendationService.list(userId));
    }

    @PostMapping("/me/recommendations/{recommendationId}/save")
    public ApiResponse<Recommendation> save(
            @RequestHeader(value = "X-User-Id", defaultValue = "demo-user") String userId,
            @PathVariable Long recommendationId) {
        return ApiResponse.ok(recommendationService.save(userId, recommendationId));
    }

    @PostMapping("/me/recommendations/{recommendationId}/feedback")
    public ApiResponse<Recommendation> feedback(
            @RequestHeader(value = "X-User-Id", defaultValue = "demo-user") String userId,
            @PathVariable Long recommendationId,
            @Valid @RequestBody RecommendationFeedbackRequest request) {
        return ApiResponse.ok(recommendationService.feedback(userId, recommendationId, request));
    }
}
