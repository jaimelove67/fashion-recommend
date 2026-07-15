package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.style.PersonalStyleProfileService;
import com.fashion.recommendation.style.StyleProfile;
import com.fashion.recommendation.weather.WeatherService;
import com.fashion.recommendation.weather.WeatherSnapshot;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import com.fashion.recommendation.wardrobe.WardrobeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final String LLM_ENGINE = "llm";
    private static final String RULE_ENGINE = "development-rule-v1";

    private final WardrobeRepository wardrobeRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationFeedbackRepository feedbackRepository;
    private final PersonalStyleProfileService profileService;
    private final WeatherService weatherService;
    private final LlmRecommendationClient llmRecommendationClient;

    public RecommendationService(
            WardrobeRepository wardrobeRepository,
            RecommendationRepository recommendationRepository,
            RecommendationFeedbackRepository feedbackRepository,
            PersonalStyleProfileService profileService,
            WeatherService weatherService,
            LlmRecommendationClient llmRecommendationClient) {
        this.wardrobeRepository = wardrobeRepository;
        this.recommendationRepository = recommendationRepository;
        this.feedbackRepository = feedbackRepository;
        this.profileService = profileService;
        this.weatherService = weatherService;
        this.llmRecommendationClient = llmRecommendationClient;
    }

    @Transactional
    public Recommendation generate(String userId, RecommendationRequest request) {
        List<WardrobeItem> wardrobe = wardrobeRepository.findByUserId(userId).stream()
                .filter(item -> !"NEEDS_MANUAL_REVIEW".equals(item.recognitionStatus()))
                .toList();
        if (wardrobe.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "请先添加并完善至少两件衣物，再生成穿搭推荐");
        }

        WeatherSnapshot weather = weatherService.current(request.city());
        List<WardrobeItem> ruleSelected = selectItems(wardrobe, weather.temperatureC());
        if (ruleSelected.size() < 2 || distinctCategoryCount(ruleSelected) < 2) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "当前衣橱缺少可组合的不同类别衣物");
        }

        StyleProfile profile = profileService.current(userId);
        RecommendationDraft draft = tryLlmRecommendation(request, wardrobe, weather, profile)
                .orElseGet(() -> buildRuleRecommendation(request, ruleSelected, weather, profile));
        Instant generatedAt = Instant.now();
        Long recommendationId = recommendationRepository.create(
                userId, request, weather, draft.summary(), draft.reason(), draft.engine(), generatedAt);
        recommendationRepository.addItems(recommendationId, draft.items());
        return get(userId, recommendationId);
    }

    public Recommendation get(String userId, Long recommendationId) {
        RecommendationRecord record = recommendationRepository.findByIdForUser(recommendationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "推荐记录不存在"));
        return toRecommendation(userId, record);
    }

    public List<Recommendation> list(String userId) {
        return recommendationRepository.findAllByUserId(userId).stream()
                .map(record -> toRecommendation(userId, record))
                .toList();
    }

    public Recommendation save(String userId, Long recommendationId) {
        if (!recommendationRepository.markSaved(recommendationId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "推荐记录不存在");
        }
        return get(userId, recommendationId);
    }

    public Recommendation feedback(String userId, Long recommendationId, RecommendationFeedbackRequest request) {
        get(userId, recommendationId);
        feedbackRepository.save(userId, recommendationId, request);
        return get(userId, recommendationId);
    }

    private Recommendation toRecommendation(String userId, RecommendationRecord record) {
        WeatherSnapshot weather = record.weatherSource() == null ? null : new WeatherSnapshot(
                record.city(),
                record.temperatureC(),
                record.apparentTemperatureC(),
                record.precipitationMm(),
                record.weatherCode(),
                record.windSpeedKmh(),
                record.weatherObservedAt(),
                record.weatherSource());
        return new Recommendation(
                record.id(),
                record.occasion(),
                record.city(),
                record.temperatureC(),
                record.summary(),
                record.reason(),
                record.engine(),
                record.saved(),
                record.generatedAt(),
                weather,
                feedbackRepository.findByRecommendationId(userId, record.id()).orElse(null),
                recommendationRepository.findItems(record.id()));
    }

    private static List<WardrobeItem> selectItems(List<WardrobeItem> wardrobe, double temperature) {
        List<String> categoryOrder = temperature < 18
                ? List.of("外套", "上装", "下装", "鞋履", "配饰")
                : List.of("上装", "下装", "鞋履", "外套", "配饰");
        Map<Long, WardrobeItem> selected = new LinkedHashMap<>();
        for (String category : categoryOrder) {
            wardrobe.stream().filter(item -> matchesCategory(item.category(), category)).findFirst()
                    .ifPresent(item -> selected.putIfAbsent(item.id(), item));
        }
        if (selected.size() < 4) {
            for (WardrobeItem item : wardrobe) {
                selected.putIfAbsent(item.id(), item);
                if (selected.size() == 4) {
                    break;
                }
            }
        }
        return new ArrayList<>(selected.values());
    }

    private Optional<RecommendationDraft> tryLlmRecommendation(
            RecommendationRequest request, List<WardrobeItem> wardrobe, WeatherSnapshot weather, StyleProfile profile) {
        try {
            LlmRecommendationContext context = new LlmRecommendationContext(
                    request.occasion().trim(), request.styleHint(), wardrobe, weather, profile);
            return llmRecommendationClient.recommend(context)
                    .flatMap(result -> validateLlmResult(result, wardrobe));
        } catch (RuntimeException exception) {
            log.warn("LLM recommendation failed; falling back to {}: {}", RULE_ENGINE, exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<RecommendationDraft> validateLlmResult(
            LlmRecommendationResult result, List<WardrobeItem> wardrobe) {
        if (result == null || !validText(result.summary(), 500) || !validText(result.reason(), 1200)
                || result.itemIds() == null || result.itemIds().size() < 2 || result.itemIds().size() > 4) {
            log.warn("LLM recommendation failed result validation; falling back to {}", RULE_ENGINE);
            return Optional.empty();
        }

        Set<Long> requestedIds = Set.copyOf(result.itemIds());
        if (requestedIds.size() != result.itemIds().size()) {
            log.warn("LLM recommendation contains duplicate item IDs; falling back to {}", RULE_ENGINE);
            return Optional.empty();
        }
        Map<Long, WardrobeItem> wardrobeById = new LinkedHashMap<>();
        wardrobe.forEach(item -> wardrobeById.put(item.id(), item));
        if (!wardrobeById.keySet().containsAll(requestedIds)) {
            log.warn("LLM recommendation contains item IDs outside the current wardrobe; falling back to {}", RULE_ENGINE);
            return Optional.empty();
        }
        List<WardrobeItem> selected = result.itemIds().stream().map(wardrobeById::get).toList();
        return Optional.of(new RecommendationDraft(
                result.summary().trim(), result.reason().trim(), LLM_ENGINE, selected));
    }

    private static RecommendationDraft buildRuleRecommendation(
            RecommendationRequest request, List<WardrobeItem> selected, WeatherSnapshot weather, StyleProfile profile) {
        String itemNames = selected.stream().map(WardrobeItem::name).reduce((left, right) -> left + "、" + right).orElse("");
        String summary = request.occasion().trim() + "推荐：" + itemNames;
        String reason = buildReason(request, selected, weather, profile);
        return new RecommendationDraft(summary, reason, RULE_ENGINE, selected);
    }

    private static boolean validText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.trim().length() <= maxLength;
    }

    private static boolean matchesCategory(String category, String target) {
        if (category == null) {
            return false;
        }
        String normalized = category.trim();
        return normalized.contains(target) || (target.equals("鞋履") && normalized.contains("鞋"));
    }

    private static long distinctCategoryCount(List<WardrobeItem> items) {
        return items.stream().map(item -> canonicalCategory(item.category())).distinct().count();
    }

    private static String canonicalCategory(String category) {
        if (category == null) {
            return "";
        }
        for (String canonical : List.of("外套", "上装", "下装", "鞋履", "配饰")) {
            if (matchesCategory(category, canonical)) {
                return canonical;
            }
        }
        return category.trim();
    }

    private static String buildReason(
            RecommendationRequest request, List<WardrobeItem> selected, WeatherSnapshot weather, StyleProfile profile) {
        StringBuilder reason = new StringBuilder()
                .append("根据当前衣橱中 ").append(selected.size()).append(" 件可组合单品，结合 ")
                .append(weather.city()).append("实况 ").append(String.format("%.1f", weather.temperatureC()))
                .append("°C、体感 ").append(String.format("%.1f", weather.apparentTemperatureC()))
                .append("°C 和").append(request.occasion().trim()).append("场景生成。规则优先保证基本类别齐全");
        if (!profile.stylePreferences().isEmpty()) {
            reason.append("，并参考已保存的“").append(String.join("、", profile.stylePreferences())).append("”风格偏好");
        }
        return reason.append("。").toString();
    }

    private record RecommendationDraft(String summary, String reason, String engine, List<WardrobeItem> items) {
    }
}
