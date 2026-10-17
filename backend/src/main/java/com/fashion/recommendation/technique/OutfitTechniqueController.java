package com.fashion.recommendation.technique;

import com.fashion.recommendation.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/outfit-techniques")
public class OutfitTechniqueController {
    private final WeeklyTechniqueService service;

    public OutfitTechniqueController(WeeklyTechniqueService service) { this.service = service; }

    @GetMapping
    public ApiResponse<WeeklyTechniqueService.TechniqueFeed> list() {
        return ApiResponse.ok(service.currentFeed());
    }
}
