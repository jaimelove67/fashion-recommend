package com.fashion.recommendation.weather;

import com.fashion.recommendation.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {
    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/current")
    public ApiResponse<WeatherSnapshot> current(@RequestParam(defaultValue = "长沙") String city) {
        return ApiResponse.ok(weatherService.current(city));
    }
}
