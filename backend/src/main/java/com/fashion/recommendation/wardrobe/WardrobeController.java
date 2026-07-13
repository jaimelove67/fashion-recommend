package com.fashion.recommendation.wardrobe;

import com.fashion.recommendation.common.ApiResponse;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
    public ApiResponse<List<WardrobeItem>> list(
            @RequestHeader(value = "X-User-Id", defaultValue = "demo-user") String userId) {
        return ApiResponse.ok(wardrobeService.list(userId));
    }

    @PostMapping
    public ApiResponse<WardrobeItem> create(
            @RequestHeader(value = "X-User-Id", defaultValue = "demo-user") String userId,
            @Valid @RequestBody WardrobeItemRequest request) {
        return ApiResponse.ok(wardrobeService.create(userId, request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<WardrobeItem> upload(
            @RequestHeader(value = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestParam("image") MultipartFile image,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String style) {
        return ApiResponse.ok(wardrobeService.upload(userId, image, name, category, color, style));
    }

    @PutMapping("/{itemId}")
    public ApiResponse<WardrobeItem> update(
            @RequestHeader(value = "X-User-Id", defaultValue = "demo-user") String userId,
            @PathVariable Long itemId,
            @Valid @RequestBody WardrobeItemRequest request) {
        return ApiResponse.ok(wardrobeService.update(userId, itemId, request));
    }

    @GetMapping("/{itemId}/image")
    public ResponseEntity<ByteArrayResource> image(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(required = false) String userId,
            @PathVariable Long itemId) {
        String effectiveUserId = headerUserId == null || headerUserId.isBlank()
                ? (userId == null || userId.isBlank() ? "demo-user" : userId)
                : headerUserId;
        var data = wardrobeService.readImage(effectiveUserId, itemId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(data.contentType()))
                .body(new ByteArrayResource(data.content()));
    }

    @DeleteMapping("/{itemId}")
    public ApiResponse<Void> delete(
            @RequestHeader(value = "X-User-Id", defaultValue = "demo-user") String userId,
            @PathVariable Long itemId) {
        wardrobeService.delete(userId, itemId);
        return ApiResponse.ok(null);
    }
}
