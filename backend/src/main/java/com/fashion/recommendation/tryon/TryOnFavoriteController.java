package com.fashion.recommendation.tryon;

import com.fashion.recommendation.common.ApiResponse;
import java.security.Principal;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/me/try-on-favorites")
public class TryOnFavoriteController {
    private final TryOnFavoriteService service;
    public TryOnFavoriteController(TryOnFavoriteService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<TryOnFavorite>> list(Principal principal) { return ApiResponse.ok(service.list(principal.getName())); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TryOnFavorite> save(Principal principal, @RequestParam String expectedUser,
            @RequestParam MultipartFile image, @RequestParam String name, @RequestParam String category,
            @RequestParam(required = false) String sourceUrl, @RequestParam String sourceKind) {
        return ApiResponse.ok(service.save(principal.getName(), expectedUser, image, name, category, sourceUrl, sourceKind));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<ByteArrayResource> image(Principal principal, @PathVariable Long id) {
        var data = service.image(principal.getName(), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(data.contentType()))
                .header("Cache-Control", "private, no-store").header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(data.content()));
    }
}
