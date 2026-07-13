package com.fashion.recommendation.trend;

import com.fashion.recommendation.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trends")
public class TrendController {
    private final TrendService trendService;

    public TrendController(TrendService trendService) {
        this.trendService = trendService;
    }

    @GetMapping
    public ApiResponse<TrendFeed> list(@RequestParam(required = false) String platform, @RequestParam(required = false) String topic) {
        return ApiResponse.ok(trendService.currentFeed(platform, topic));
    }
}
