package com.fashion.recommendation.weather;

import com.fashion.recommendation.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {
    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/current")
    public ApiResponse<WeatherSnapshot> current(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        if (latitude != null || longitude != null) {
            if (latitude == null || longitude == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "定位坐标必须同时提供经度和纬度");
            }
            return ApiResponse.ok(weatherService.currentAt(latitude, longitude));
        }
        return ApiResponse.ok(weatherService.current(StringUtils.hasText(city) ? city : "长沙"));
    }
}
