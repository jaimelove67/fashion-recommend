package com.fashion.recommendation.wardrobe;

import com.fashion.recommendation.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/me/wardrobe")
public class WardrobeController {
    private final WardrobeService wardrobeService;

    public WardrobeController(WardrobeService wardrobeService) {
        this.wardrobeService = wardrobeService;
    }

    @GetMapping
    public ApiResponse<List<WardrobeItem>> list(Principal principal) {
        return ApiResponse.ok(wardrobeService.list(principal.getName()));
    }

    @PostMapping
    public ApiResponse<WardrobeItem> create(
            Principal principal,
            @Valid @RequestBody WardrobeItemRequest request) {
        return ApiResponse.ok(wardrobeService.create(principal.getName(), request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<WardrobeItem> upload(
            Principal principal,
            @RequestParam("image") MultipartFile image,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String style,
            @RequestParam(defaultValue = "false") boolean allowAiRecognition) {
        return ApiResponse.ok(wardrobeService.upload(
                principal.getName(), image, name, category, color, style, allowAiRecognition));
    }

    @PutMapping("/{itemId}")
    public ApiResponse<WardrobeItem> update(
            Principal principal,
            @PathVariable Long itemId,
            @Valid @RequestBody WardrobeItemRequest request) {
        return ApiResponse.ok(wardrobeService.update(principal.getName(), itemId, request));
    }

    @GetMapping("/{itemId}/image")
    public ResponseEntity<ByteArrayResource> image(
            Principal principal,
            @PathVariable Long itemId) {
        var data = wardrobeService.readImage(principal.getName(), itemId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(data.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(data.content()));
    }

    @DeleteMapping("/{itemId}")
    public ApiResponse<Void> delete(
            Principal principal,
            @PathVariable Long itemId) {
        wardrobeService.delete(principal.getName(), itemId);
        return ApiResponse.ok(null);
    }
}
