package com.fashion.recommendation.style;

import com.fashion.recommendation.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StyleProfile> uploadPhoto(Principal principal, @RequestParam("photo") MultipartFile photo) {
        return ApiResponse.ok(profileService.uploadPhoto(principal.getName(), photo));
    }

    @GetMapping("/photo")
    public ResponseEntity<ByteArrayResource> photo(Principal principal) {
        var image = profileService.readPhoto(principal.getName());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff").body(new ByteArrayResource(image.content()));
    }

    @PostMapping("/analyze")
    public ApiResponse<StyleProfile> analyze(Principal principal, @Valid @RequestBody PersonalStyleAnalyzeRequest request) {
        return ApiResponse.ok(profileService.analyze(principal.getName(), request.allowAiAnalysis()));
    }

    @PostMapping("/outfit-model")
    public ApiResponse<StyleProfile> outfitModel(Principal principal, @Valid @RequestBody OutfitModelPreferenceRequest request) {
        return ApiResponse.ok(profileService.updateOutfitModelPreference(principal.getName(), request.usePersonalPhotoForOutfit()));
    }

    @PostMapping("/analysis")
    public ApiResponse<StyleProfile> correctAnalysis(Principal principal, @Valid @RequestBody PersonalStyleAnalysis analysis) {
        return ApiResponse.ok(profileService.correctAnalysis(principal.getName(), analysis));
    }
}
