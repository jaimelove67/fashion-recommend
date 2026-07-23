package com.fashion.recommendation.style;

import com.fashion.recommendation.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/style-profile")
public class StyleProfileController {
    private final PersonalStyleProfileService profileService;

    public StyleProfileController(PersonalStyleProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<StyleProfile> current(Principal principal) {
        return ApiResponse.ok(profileService.current(principal.getName()));
    }

    @PostMapping("/refresh")
    public ApiResponse<StyleProfile> refresh(
            Principal principal,
            @Valid @RequestBody StyleProfileRefreshRequest request) {
        return ApiResponse.ok(profileService.refresh(principal.getName(), request));
    }
}
