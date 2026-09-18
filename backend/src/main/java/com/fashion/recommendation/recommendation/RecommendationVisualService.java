package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.style.PersonalStyleProfileService;
import com.fashion.recommendation.style.StyleProfile;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import com.fashion.recommendation.wardrobe.WardrobeRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationVisualService {
    private final RecommendationRepository recommendationRepository;
    private final WardrobeRepository wardrobeRepository;
    private final PersonalStyleProfileService profileService;
    private final BailianImageGenerationClient imageGenerationClient;

    public RecommendationVisualService(
            RecommendationRepository recommendationRepository,
            WardrobeRepository wardrobeRepository,
            PersonalStyleProfileService profileService,
            BailianImageGenerationClient imageGenerationClient) {
        this.recommendationRepository = recommendationRepository;
        this.wardrobeRepository = wardrobeRepository;
        this.profileService = profileService;
        this.imageGenerationClient = imageGenerationClient;
    }

    public RecommendationVisualResponse generate(String userId, Long recommendationId) {
        RecommendationRecord recommendation = recommendationRepository.findByIdForUser(recommendationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "推荐记录不存在"));
        StyleProfile profile = profileService.current(userId);
        String gender = normalizeGender(profile.gender());
        if (gender == null) {
            return unavailable(null, null, gender, 0, "请先在个人档案中选择每日模特性别");
        }

        Map<Long, WardrobeItem> currentWardrobe = new LinkedHashMap<>();
        wardrobeRepository.findByUserId(userId).stream()
                .filter(RecommendationVisualService::isUsable)
                .forEach(item -> currentWardrobe.put(item.id(), item));
        List<WardrobeItem> items = recommendationRepository.findItems(recommendation.id()).stream()
                .map(item -> currentWardrobe.get(item.id()))
                .filter(item -> item != null)
                .distinct()
                .limit(4)
                .toList();
        if (items.size() < 2) {
            return unavailable(null, null, gender, items.size(), "当前搭配中没有足够的已确认衣橱单品");
        }

        OutfitImageGenerationResult result = imageGenerationClient.generate(
                gender, recommendation.occasion(), recommendation.city(), recommendation.temperatureC(), items);
        return new RecommendationVisualResponse(
                result.status(), result.imageUrl(), result.model(), gender, items.size(), result.message());
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
        return new RecommendationVisualResponse("UNAVAILABLE", imageUrl, model, gender, itemCount, message);
    }
}
