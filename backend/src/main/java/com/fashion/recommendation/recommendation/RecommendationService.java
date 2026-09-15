package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.style.PersonalStyleProfileService;
import com.fashion.recommendation.style.StyleProfile;
import com.fashion.recommendation.weather.WeatherService;
import com.fashion.recommendation.weather.WeatherSnapshot;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import com.fashion.recommendation.wardrobe.WardrobeRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationService {
    private static final long MAX_HISTORY_OFFSET = 1_000_000L;

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final String LLM_ENGINE = "llm";
    private static final String RULE_ENGINE = "development-rule-v1";
    private static final double NEUTRAL_RATING = 3.0;

    private final WardrobeRepository wardrobeRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationFeedbackRepository feedbackRepository;
    private final PersonalStyleProfileService profileService;
    private final WeatherService weatherService;
    private final LlmRecommendationClient llmRecommendationClient;
    private final TransactionTemplate transactionTemplate;
    private final com.fashion.recommendation.trend.TrendService trendService;

    public RecommendationService(
            WardrobeRepository wardrobeRepository,
            RecommendationRepository recommendationRepository,
            RecommendationFeedbackRepository feedbackRepository,
            PersonalStyleProfileService profileService,
            WeatherService weatherService,
            LlmRecommendationClient llmRecommendationClient,
            TransactionTemplate transactionTemplate,
            com.fashion.recommendation.trend.TrendService trendService) {
        this.wardrobeRepository = wardrobeRepository;
        this.recommendationRepository = recommendationRepository;
        this.feedbackRepository = feedbackRepository;
        this.profileService = profileService;
        this.weatherService = weatherService;
        this.llmRecommendationClient = llmRecommendationClient;
        this.transactionTemplate = transactionTemplate;
        this.trendService = trendService;
    }

    public Recommendation generate(String userId, RecommendationRequest request) {
        Instant startedAt = Instant.now();
        TrendReference reference = null;
        if (request.trendId() != null && !request.trendId().isBlank()) {
            var item = trendService.reference(request.trendId());
            reference = new TrendReference(item.id(), item.title(), item.sourceUrl(), item.topicTags(), item.summary());
        }
        List<WardrobeItem> wardrobe = wardrobeRepository.findByUserId(userId).stream()
                .filter(item -> !"NEEDS_MANUAL_REVIEW".equals(item.recognitionStatus()))
                .toList();
        if (wardrobe.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "请先添加并完善至少两件衣物，再生成穿搭推荐");
        }

        Map<Long, Double> itemRatings = feedbackRepository.averageRatingByItem(userId);
        WeatherSnapshot weather = weatherService.current(request.city());
        List<WardrobeItem> ruleSelected = selectItems(wardrobe, weather.temperatureC(), itemRatings,
                reference == null ? List.of() : reference.styleTags());
        if (ruleSelected.size() < 2 || distinctCategoryCount(ruleSelected) < 2) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "当前衣橱缺少可组合的不同类别衣物");
        }

        StyleProfile profile = profileService.current(userId);
        RecommendationAttempt attempt = tryLlmRecommendation(request, wardrobe, weather, profile, itemRatings, reference);
        RecommendationDraft draft = attempt.draft()
                .orElseGet(() -> buildRuleRecommendation(request, ruleSelected, weather, profile));
        long generationLatencyMs = Math.max(0L, Duration.between(startedAt, Instant.now()).toMillis());
        RecommendationAudit audit = toAudit(draft, attempt, generationLatencyMs);

        // Only the recommendation + items writes run inside a database transaction. Weather, the
        // LLM call and rule selection all happen above, outside the write transaction, so a slow
        // external provider cannot hold a database write open.
        Long recommendationId = transactionTemplate.execute(status -> {
            Long id = recommendationRepository.create(
                    userId, request, weather, draft.summary(), draft.reason(), draft.engine(), audit, Instant.now());
            recommendationRepository.addItems(id, draft.items());
            return id;
        });
        return get(userId, recommendationId);
    }

    public Recommendation get(String userId, Long recommendationId) {
        RecommendationRecord record = recommendationRepository.findByIdForUser(recommendationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "推荐记录不存在"));
        return toRecommendation(userId, record);
    }

    public RecommendationPage list(String userId, int page, int size) {
        long offset = (long) page * size;
        if (page < 0 || size < 1 || size > 50 || offset > MAX_HISTORY_OFFSET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法");
        }
        List<RecommendationRecord> records = recommendationRepository.findPageByUserId(userId, page, size);
        List<Long> recommendationIds = records.stream().map(RecommendationRecord::id).toList();
        Map<Long, RecommendationFeedback> feedbackByRecommendation =
                feedbackRepository.findByRecommendationIds(userId, recommendationIds);
        Map<Long, List<WardrobeItem>> itemsByRecommendation =
                recommendationRepository.findItemsByRecommendationIds(recommendationIds);
        List<Recommendation> content = records.stream()
                .map(record -> toRecommendation(
                        record,
                        feedbackByRecommendation.get(record.id()),
                        itemsByRecommendation.getOrDefault(record.id(), List.of())))
                .toList();
        long totalElements = recommendationRepository.countByUserId(userId);
        long nextOffset = ((long) page + 1L) * size;
        return new RecommendationPage(content, totalElements, page, size,
                nextOffset <= MAX_HISTORY_OFFSET && nextOffset < totalElements);
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
        return toRecommendation(
                record,
                feedbackRepository.findByRecommendationId(userId, record.id()).orElse(null),
                recommendationRepository.findItems(record.id()));
    }

    private Recommendation toRecommendation(
            RecommendationRecord record,
            RecommendationFeedback feedback,
            List<WardrobeItem> items) {
        WeatherSnapshot weather = record.weatherSource() == null ? null : new WeatherSnapshot(
                record.city(),
                record.temperatureC(),
                record.apparentTemperatureC(),
                record.precipitationMm(),
                record.weatherCode(),
                record.windSpeedKmh(),
                record.weatherObservedAt(),
                record.weatherSource());
        RecommendationAudit audit = new RecommendationAudit(
                record.engine(),
                record.fallbackReason(),
                record.modelName(),
                record.promptVersion(),
                record.providerCallId(),
                record.promptTokens(),
                record.completionTokens(),
                record.totalTokens(),
                record.generationLatencyMs());
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
                feedback,
                items,
                audit);
    }

    private static List<WardrobeItem> selectItems(
            List<WardrobeItem> wardrobe, double temperature, Map<Long, Double> itemRatings, List<String> referenceTags) {
        List<String> categoryOrder = temperature < 18
                ? List.of("外套", "上装", "下装", "鞋履", "配饰")
                : List.of("上装", "下装", "鞋履", "外套", "配饰");
        List<WardrobeItem> ranked = wardrobe.stream()
                .sorted(Comparator.comparingDouble((WardrobeItem item) -> feedbackScore(item, itemRatings)
                        + referenceTags.stream().filter(tag -> (item.name() + " " + item.style() + " " + item.category()).contains(tag)).count()).reversed())
                .toList();
        Map<Long, WardrobeItem> selected = new LinkedHashMap<>();
        for (String category : categoryOrder) {
            ranked.stream().filter(item -> matchesCategory(item.category(), category)).findFirst()
                    .ifPresent(item -> selected.putIfAbsent(item.id(), item));
        }
        if (selected.size() < 4) {
            for (WardrobeItem item : ranked) {
                selected.putIfAbsent(item.id(), item);
                if (selected.size() == 4) {
                    break;
                }
            }
        }
        return new ArrayList<>(selected.values());
    }

    private static double feedbackScore(WardrobeItem item, Map<Long, Double> itemRatings) {
        return itemRatings.getOrDefault(item.id(), NEUTRAL_RATING);
    }

    private RecommendationAttempt tryLlmRecommendation(
            RecommendationRequest request, List<WardrobeItem> wardrobe, WeatherSnapshot weather, StyleProfile profile,
            Map<Long, Double> itemRatings, TrendReference reference) {
        LlmRecommendationContext context = new LlmRecommendationContext(
                request.occasion().trim(), request.styleHint(), wardrobe, weather, profile, itemRatings, reference);
        Optional<LlmRecommendationResult> optionalResult;
        try {
            optionalResult = llmRecommendationClient.recommend(context);
        } catch (LlmRecommendationException exception) {
            log.warn("LLM recommendation failed ({}); falling back to {}: {}",
                    exception.reason(), RULE_ENGINE, exception.getMessage());
            return RecommendationAttempt.fallback(exception.reason());
        } catch (RuntimeException exception) {
            log.warn("LLM recommendation failed; falling back to {}: {}", RULE_ENGINE, exception.getMessage());
            return RecommendationAttempt.fallback(RecommendationFallbackReason.REQUEST_FAILED);
        }
        if (optionalResult.isEmpty()) {
            return RecommendationAttempt.fallback(RecommendationFallbackReason.NO_API_KEY);
        }
        return validateLlmResult(optionalResult.get(), wardrobe);
    }

    private RecommendationAttempt validateLlmResult(
            LlmRecommendationResult result, List<WardrobeItem> wardrobe) {
        if (result == null || !validText(result.summary(), 500) || !validText(result.reason(), 1200)
                || result.itemIds() == null || result.itemIds().size() < 2 || result.itemIds().size() > 4) {
            log.warn("LLM recommendation failed result validation; falling back to {}", RULE_ENGINE);
            return RecommendationAttempt.fallback(RecommendationFallbackReason.RESULT_INVALID);
        }

        Set<Long> requestedIds = Set.copyOf(result.itemIds());
        if (requestedIds.size() != result.itemIds().size()) {
            log.warn("LLM recommendation contains duplicate item IDs; falling back to {}", RULE_ENGINE);
            return RecommendationAttempt.fallback(RecommendationFallbackReason.DUPLICATE_ITEM_IDS);
        }
        Map<Long, WardrobeItem> wardrobeById = new LinkedHashMap<>();
        wardrobe.forEach(item -> wardrobeById.put(item.id(), item));
        if (!wardrobeById.keySet().containsAll(requestedIds)) {
            log.warn("LLM recommendation contains item IDs outside the current wardrobe; falling back to {}", RULE_ENGINE);
            return RecommendationAttempt.fallback(RecommendationFallbackReason.FOREIGN_ITEM_IDS);
        }
        List<WardrobeItem> selected = result.itemIds().stream().map(wardrobeById::get).toList();
        if (distinctCategoryCount(selected) < 2) {
            log.warn("LLM recommendation does not contain distinct garment categories; falling back to {}", RULE_ENGINE);
            return RecommendationAttempt.fallback(RecommendationFallbackReason.SAME_CATEGORY);
        }
        return RecommendationAttempt.llm(new RecommendationDraft(
                result.summary().trim(), result.reason().trim(), LLM_ENGINE, selected,
                result.modelName(), result.promptVersion(), result.providerCallId(),
                result.promptTokens(), result.completionTokens(), result.totalTokens()));
    }

    private static RecommendationDraft buildRuleRecommendation(
            RecommendationRequest request, List<WardrobeItem> selected, WeatherSnapshot weather, StyleProfile profile) {
        String itemNames = selected.stream().map(WardrobeItem::name).reduce((left, right) -> left + "、" + right).orElse("");
        String summary = request.occasion().trim() + "推荐：" + itemNames;
        String reason = buildReason(request, selected, weather, profile);
        return new RecommendationDraft(summary, reason, RULE_ENGINE, selected, null, null, null, null, null, null);
    }

    private static RecommendationAudit toAudit(
            RecommendationDraft draft, RecommendationAttempt attempt, long generationLatencyMs) {
        if (LLM_ENGINE.equals(draft.engine())) {
            return new RecommendationAudit(LLM_ENGINE, null, draft.modelName(), draft.promptVersion(),
                    draft.providerCallId(), draft.promptTokens(), draft.completionTokens(), draft.totalTokens(),
                    generationLatencyMs);
        }
        return new RecommendationAudit(RULE_ENGINE, attempt.fallbackReason(), null, null, null, null, null, null,
                generationLatencyMs);
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
        boolean configuredDemo = "configured-demo".equals(weather.source());
        StringBuilder reason = new StringBuilder()
                .append("根据当前衣橱中 ").append(selected.size()).append(" 件可组合单品，结合 ")
                .append(weather.city()).append(configuredDemo ? "天气" : "实况 ")
                .append(String.format("%.1f", weather.temperatureC()))
                .append("°C、体感 ").append(String.format("%.1f", weather.apparentTemperatureC()))
                .append("°C 和").append(request.occasion().trim()).append("场景生成。规则优先保证基本类别齐全");
        if (!profile.stylePreferences().isEmpty()) {
            reason.append("，并参考已保存的“").append(String.join("、", profile.stylePreferences())).append("”风格偏好");
        }
        if (request.trendId() != null && !request.trendId().isBlank())
            reason.append("。已按参考风格标签匹配现有衣物；当前为基础规则结果，不代表复现参考图");
        return reason.append("。").toString();
    }

    private record RecommendationDraft(
            String summary,
            String reason,
            String engine,
            List<WardrobeItem> items,
            String modelName,
            String promptVersion,
            String providerCallId,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens) {
    }

    private record RecommendationAttempt(Optional<RecommendationDraft> draft, String fallbackReason) {
        static RecommendationAttempt llm(RecommendationDraft draft) {
            return new RecommendationAttempt(Optional.of(draft), null);
        }

        static RecommendationAttempt fallback(String reason) {
            return new RecommendationAttempt(Optional.empty(), reason);
        }
    }
}
