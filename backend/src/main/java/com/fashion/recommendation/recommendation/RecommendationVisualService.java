package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.style.PersonalStyleProfileService;
import com.fashion.recommendation.style.StyleProfile;
import com.fashion.recommendation.storage.StoredImageData;
import com.fashion.recommendation.storage.GeneratedImageDownloader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import com.fashion.recommendation.wardrobe.WardrobeRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationVisualService {
    private final RecommendationRepository recommendationRepository;
    private final WardrobeRepository wardrobeRepository;
    private final PersonalStyleProfileService profileService;
    private final BailianImageGenerationClient imageGenerationClient;
    private final RecommendationVisualRepository visuals;
    private final GeneratedImageDownloader downloader;
    private final ObjectMapper mapper;

    public RecommendationVisualService(
            RecommendationRepository recommendationRepository,
            WardrobeRepository wardrobeRepository,
            PersonalStyleProfileService profileService,
            BailianImageGenerationClient imageGenerationClient,
            RecommendationVisualRepository visuals, GeneratedImageDownloader downloader, ObjectMapper mapper) {
        this.recommendationRepository = recommendationRepository;
        this.wardrobeRepository = wardrobeRepository;
        this.profileService = profileService;
        this.imageGenerationClient = imageGenerationClient;
        this.visuals = visuals;
        this.downloader = downloader;
        this.mapper = mapper;
    }

    public RecommendationVisualResponse generate(String userId, Long recommendationId) {
        return generate(userId, recommendationId, false);
    }

    public RecommendationVisualResponse generate(String userId, Long recommendationId, boolean force) {
        return visual(userId, recommendationId, force, true, null);
    }

    public RecommendationVisualResponse current(String userId, Long recommendationId) {
        return visual(userId, recommendationId, false, false, null);
    }

    public RecommendationVisualResponse generate(String userId, Long recommendationId, boolean force, VisualItemsRequest request) {
        return visual(userId, recommendationId, force, true, request);
    }

    public RecommendationVisualRepository.Quota quota(String userId) { return visuals.quota(userId); }

    public StoredImageData image(String userId, Long recommendationId, String assetId) {
        return visuals.image(userId, recommendationId, assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "效果图不存在"));
    }

    private RecommendationVisualResponse visual(String userId, Long recommendationId, boolean force, boolean generate, VisualItemsRequest request) {
        RecommendationRecord recommendation = recommendationRepository.findByIdForUser(recommendationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "推荐记录不存在"));
        StyleProfile profile = profileService.current(userId);
        String gender = normalizeGender(profile.gender());
        if (gender == null) {
            return unavailable(null, null, gender, 0, "请在个人形象档案中设置模特性别。");
        }

        Map<Long, WardrobeItem> currentWardrobe = new LinkedHashMap<>();
        wardrobeRepository.findByUserId(userId).stream()
                .filter(RecommendationVisualService::isUsable)
                .forEach(item -> currentWardrobe.put(item.id(), item));
        List<WardrobeItem> items = request != null && request.items() != null
                ? selectedItems(request, currentWardrobe) : recommendationRepository.findItems(recommendation.id()).stream()
                .map(item -> currentWardrobe.get(item.id()))
                .filter(item -> item != null)
                .distinct()
                .limit(4)
                .toList();
        if (items.size() < 2) {
            return unavailable(null, null, gender, items.size(), "请至少选择两件可用单品");
        }

        if (profile.usePersonalPhotoForOutfit() && profile.photoObjectKey() == null) {
            return unavailable(null, null, gender, items.size(), "请上传个人照片，或切换为默认模特。");
        }
        String fingerprint = fingerprint(profile, gender, items);
        var cached = visuals.find(recommendationId, fingerprint).orElse(null);
        if (!generate || (!force && cached != null && cached.assetId() != null)) {
            return response(recommendationId, cached, gender, items.size(), profile);
        }
        String token = visuals.reserve(recommendationId, fingerprint, force);
        if (token == null) {
            return response(recommendationId, visuals.find(recommendationId, fingerprint).orElse(null), gender, items.size(), profile);
        }
        try {
            StoredImageData personalPhoto = profile.usePersonalPhotoForOutfit()
                    ? profileService.readOutfitPhoto(userId, profile.photoObjectKey()) : null;
            OutfitImageGenerationResult result = personalPhoto == null
                    ? imageGenerationClient.generate(gender, recommendation.occasion(), recommendation.city(), recommendation.temperatureC(), items)
                    : imageGenerationClient.generate(gender, recommendation.occasion(), recommendation.city(), recommendation.temperatureC(), items, personalPhoto);
            if (result.succeeded()) {
                StoredImageData image = downloader.download(result.imageUrl());
                visuals.finish(recommendationId, fingerprint, token, image, result.model());
                return response(recommendationId, visuals.find(recommendationId, fingerprint).orElse(null), gender, items.size(), profile);
            }
            return new RecommendationVisualResponse(result.status(), null, result.model(), gender, items.size(), result.message(),
                    profile.usePersonalPhotoForOutfit() ? "PERSONAL" : "DEFAULT");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return new RecommendationVisualResponse("FAILED", null, null, gender, items.size(), "效果图保存失败，请稍后重试。",
                    profile.usePersonalPhotoForOutfit() ? "PERSONAL" : "DEFAULT");
        } finally {
            visuals.release(recommendationId, fingerprint, token);
        }
    }

    private List<WardrobeItem> selectedItems(VisualItemsRequest request, Map<Long, WardrobeItem> wardrobe) {
        return request.items().stream().map(item -> {
            if (item.wardrobeId() != null) {
                WardrobeItem owned = wardrobe.get(item.wardrobeId());
                if (owned == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选衣橱单品不可用");
                return owned;
            }
            String data = item.imageData();
            if (data == null || !data.matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传 JPG、PNG 或 WebP 单品图片");
            }
            try {
                byte[] bytes = java.util.Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
                if (bytes.length > 2 * 1024 * 1024) throw new IllegalArgumentException();
                try (var input = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
                    var readers = javax.imageio.ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw new IllegalArgumentException();
                    var reader = readers.next();
                    try {
                        reader.setInput(input);
                        if (reader.getWidth(0) < 240 || reader.getHeight(0) < 240
                                || reader.getWidth(0) > 8000 || reader.getHeight(0) > 8000) throw new IllegalArgumentException();
                    } finally { reader.dispose(); }
                }
            } catch (Exception exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单品图片需为有效图片，大小不超过2MB，宽高为240至8000像素");
            }
            return new WardrobeItem(null, item.name() == null ? "上传单品" : item.name(),
                    item.category(), "", "", data, null);
        }).toList();
    }

    private RecommendationVisualResponse response(Long id, RecommendationVisualRepository.Visual visual,
            String gender, int count, StyleProfile profile) {
        String url = visual != null && visual.assetId() != null
                ? "/api/v1/me/recommendations/" + id + "/visual/images/" + visual.assetId() : null;
        boolean pending = visual != null && visual.pending();
        return new RecommendationVisualResponse(pending ? "PENDING" : url != null ? "SUCCEEDED" : "UNAVAILABLE", url,
                visual == null ? null : visual.model(), gender, count,
                pending ? "正在生成穿搭效果图…" : url == null ? "效果图尚未生成，请重试。" : null,
                profile.usePersonalPhotoForOutfit() ? "PERSONAL" : "DEFAULT");
    }

    private String fingerprint(StyleProfile profile, String gender, List<WardrobeItem> items) {
        try {
            var inputs = List.of(gender, profile.usePersonalPhotoForOutfit(),
                    profile.usePersonalPhotoForOutfit() ? profile.photoObjectKey() : "DEFAULT", items,
                    items.stream().map(item -> item.imageObjectKey() == null ? "" : item.imageObjectKey()).toList());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(inputs)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not identify outfit visual inputs", exception);
        }
    }

    private static boolean isUsable(WardrobeItem item) {
        return item != null && item.id() != null && !"NEEDS_MANUAL_REVIEW".equals(item.recognitionStatus());
    }

    private static String normalizeGender(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "MALE".equals(normalized) || "FEMALE".equals(normalized) ? normalized : null;
    }

    private static RecommendationVisualResponse unavailable(
            String imageUrl, String model, String gender, int itemCount, String message) {
        return new RecommendationVisualResponse("UNAVAILABLE", imageUrl, model, gender, itemCount, message, null);
    }
}
