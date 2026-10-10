package com.fashion.recommendation.trend;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrendService {
    private static final List<String> SOCIAL_SOURCES = List.of("douyin", "weibo", "xiaohongshu");
    private static final String CONFIGURED_DEMO_SOURCE = "configured-demo";
    private static final int SOCIAL_ITEMS_PER_SUPPLEMENTAL = 4;
    /**
     * The configured trend demo is intentionally anchored to a capture time.  It must never
     * rewrite old samples to {@code Instant.now()}, otherwise a presentation fallback would look
     * like a live platform import and would also create fake interaction growth.
     */
    private static final Instant CONFIGURED_DEMO_CAPTURED_AT = Instant.parse("2026-09-28T08:00:00Z");
    private static final List<TrendItem> CONFIGURED_DEMO_ITEMS = configuredDemoItems();
    private final List<TrendSourceAdapter> sources;
    private final TrendRepository repository;
    private final boolean hotBoardsEnabled;
    private final boolean configuredDemoEnabled;

    @Autowired
    public TrendService(List<TrendSourceAdapter> sources, TrendRepository repository,
            @Value("${app.trends.mainstream-followers:100000}") long mainstreamFollowers,
            @Value("${app.trends.niche-share:0.25}") double nicheShare,
            @Value("${app.trends.hot-boards-enabled:false}") boolean hotBoardsEnabled,
            @Value("${app.trends.configured-demo-enabled:false}") boolean configuredDemoEnabled) {
        if (mainstreamFollowers < 1) throw new IllegalArgumentException("mainstream followers must be positive");
        if (!Double.isFinite(nicheShare) || nicheShare < 0 || nicheShare > 1)
            throw new IllegalArgumentException("niche share must be between 0 and 1");
        this.sources = sources;
        this.repository = repository;
        this.mainstreamFollowers = mainstreamFollowers;
        this.nicheShare = nicheShare;
        this.hotBoardsEnabled = hotBoardsEnabled;
        this.configuredDemoEnabled = configuredDemoEnabled;
    }

    /** Compatibility overload for tests and embedders that supplied the pre-demo tuning knobs. */
    public TrendService(List<TrendSourceAdapter> sources, TrendRepository repository,
            long mainstreamFollowers, double nicheShare, boolean hotBoardsEnabled) {
        this(sources, repository, mainstreamFollowers, nicheShare, hotBoardsEnabled, false);
    }

    public TrendService(List<TrendSourceAdapter> sources, TrendRepository repository) {
        this(sources, repository, 100_000, 0.25, false, false);
    }

    /** Test/embedding constructor that keeps the two-argument constructor's safe default. */
    public TrendService(List<TrendSourceAdapter> sources, TrendRepository repository,
            boolean configuredDemoEnabled) {
        this(sources, repository, 100_000, 0.25, false, configuredDemoEnabled);
    }

    private static boolean isImagePost(TrendItem item) {
        TrendEvidence evidence = item.evidence();
        String mediaType = evidence == null ? null : evidence.mediaType();
        if (mediaType != null && !mediaType.isBlank()
                && !List.of("image", "post", "photo").contains(mediaType.toLowerCase(java.util.Locale.ROOT))) return false;
        return (item.imageUrl() != null && !item.imageUrl().isBlank())
                || (evidence != null && evidence.images() != null && evidence.images().stream()
                        .anyMatch(url -> url != null && !url.isBlank()));
    }

    private final long mainstreamFollowers;
    private final double nicheShare;

    @Scheduled(initialDelayString = "${app.trends.initial-delay:10000}")
    public void refreshOnStartup() {
        refresh();
    }

    @Scheduled(cron = "${app.trends.refresh-cron:0 0 12 * * *}", zone = "Asia/Shanghai")
    public synchronized void refresh() {
        for (TrendSourceAdapter source : sources) {
            if ("editorial".equalsIgnoreCase(source.platform())) continue;
            Instant now = Instant.now();
            try {
                List<TrendItem> items = source.fetchPublicSnapshots();
                List<TrendItem> visibleItems = items.stream()
                        .filter(TrendService::isImagePost)
                        .filter(item -> !TrendTopics.excludedShowOrCelebrity(item)).toList();
                for (TrendItem item : visibleItems) repository.save(source.platform(), normalize(item, source.scoreLabel()));
                if (visibleItems.isEmpty() && SOCIAL_SOURCES.contains(source.platform()) && !source.emptyResultIsHealthy()) {
                    // With hot boards off, an absent import or endpoint means unconfigured, not a failed scrape.
                    repository.clearStatus(source.platform());
                    continue;
                }
                repository.status(source.platform(), now, !visibleItems.isEmpty() || source.emptyResultIsHealthy(), visibleItems.size(),
                        visibleItems.isEmpty()
                                ? (source.emptyResultIsHealthy() ? "来源可用，本次没有穿搭相关内容" : "来源未接通或本次未返回穿搭内容")
                                : "采集完成");
            } catch (RuntimeException e) {
                repository.status(source.platform(), now, false, 0, "采集失败，请检查来源连接或会话；保留上次结果");
            }
        }
    }
    static TrendItem normalize(TrendItem item, String scoreLabel) {
        String id = item.id().startsWith(item.platform() + ":") ? item.id() : item.platform() + ":" + item.id();
        TrendEvidence evidence = item.evidence() == null
                ? new TrendEvidence("", "image", item.imageUrl() == null ? List.of() : List.of(item.imageUrl()), null, null, null, null, scoreLabel, null)
                : item.evidence();
        return new TrendItem(id, item.platform(), item.title(), item.topicTags(), item.heatScore(), item.publishedAt(),
                item.fetchedAt(), item.sourceUrl(), item.stale(), item.imageUrl(), item.summary(), evidence);
    }
    public TrendFeed currentFeed(String platform, String topic) { return currentFeed(platform, topic, "week"); }
    public TrendFeed currentFeed(String platform, String topic, String period) {
        if (!List.of("day", "week").contains(period))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "时间范围只能为 day 或 week");
        Instant now = Instant.now();
        Instant cutoff = now.minus(period.equals("day") ? 1 : 7, ChronoUnit.DAYS);
        List<TrendItem> scored = new ArrayList<>();
        List<TrendItem> stored = hotBoardsEnabled
                ? repository.sinceIncludingHotBoards(cutoff)
                : repository.since(cutoff);
        Map<String, Integer> observedCounts = new HashMap<>();
        Map<String, Integer> eligibleCounts = new HashMap<>();
        Map<String, List<String>> exclusionReasons = new HashMap<>();
        for (TrendItem item : stored) {
            String sourceId = item.platform();
            observedCounts.merge(sourceId, 1, Integer::sum);
            if (!isImagePost(item)) {
                noteExclusion(exclusionReasons, sourceId, "不符合展示规则（仅收录图文帖子，不含视频和纯文字）");
                continue;
            }
            if (TrendTopics.excludedShowOrCelebrity(item)) {
                noteExclusion(exclusionReasons, sourceId, "不符合展示规则（红毯、秀场或明星内容）");
                continue;
            }
            if (item.publishedAt() == null || item.fetchedAt() == null) {
                noteExclusion(exclusionReasons, sourceId, "时间字段不完整");
                continue;
            }
            if (item.publishedAt().isAfter(now) || item.fetchedAt().isAfter(now.plusSeconds(60))) {
                noteExclusion(exclusionReasons, sourceId, "时间字段超出当前时间");
                continue;
            }
            TrendEvidence e = item.evidence();
            if (!hotBoardsEnabled && e != null && "board".equalsIgnoreCase(e.mediaType())) {
                noteExclusion(exclusionReasons, sourceId, "不符合展示规则（匿名热榜默认只作为补充）");
                continue;
            }
            Long growth = repository.growth(item, cutoff);
            if (item.publishedAt().isBefore(cutoff) && (growth == null || growth <= 0)) {
                noteExclusion(exclusionReasons, sourceId, "超过当前时间窗口或没有可验证的互动增长");
                continue;
            }
            eligibleCounts.merge(sourceId, 1, Integer::sum);
            int score = item.heatScore();
            String label = e == null ? "来源评分" : e.scoreLabel();
            if (e != null && e.hasCounters()) {
                double engagement = value(e.likes()) + 2d * value(e.favorites()) + 2d * value(e.comments()) + 3d * value(e.reposts());
                double hours = Math.max(0, Duration.between(item.publishedAt(), now).toHours());
                score = (int) Math.min(100, Math.round(12 * Math.log1p(engagement) / (1 + hours / 168)));
                label = "平台内互动评分";
            }
            boolean stale = item.stale() || item.fetchedAt().isBefore(now.minus(24, ChronoUnit.HOURS));
            TrendEvidence rankedEvidence = e == null
                    ? (SOCIAL_SOURCES.contains(item.platform())
                            ? new TrendEvidence("", "image", item.imageUrl() == null ? List.of() : List.of(item.imageUrl()),
                                    null, null, null, null, label, growth, null, "unclassified")
                            : null)
                    : e.scored(label, growth);
            if (rankedEvidence != null && SOCIAL_SOURCES.contains(item.platform())) {
                rankedEvidence = rankedEvidence.withCreatorTier(classifyCreator(
                        rankedEvidence.authorFollowers(), rankedEvidence.authorFollowersLabel()));
            }
            scored.add(new TrendItem(item.id(), item.platform(), item.title(), item.topicTags(), score,
                    item.publishedAt(), item.fetchedAt(), item.sourceUrl(), stale, item.imageUrl(), item.summary(),
                    rankedEvidence));
        }
        // Rank within platforms, then prioritize creator posts and other approved non-publisher sources.
        Map<String, List<TrendItem>> groups = new LinkedHashMap<>();
        for (TrendItem item : scored) groups.computeIfAbsent(item.platform(), ignored -> new ArrayList<>()).add(item);
        groups.values().forEach(items -> items.sort(Comparator
                .comparingInt((TrendItem item) -> hasVisualShare(item) ? 0 : 1)
                .thenComparing(Comparator.comparingLong(TrendService::creatorReach).reversed())
                .thenComparing(Comparator.comparingInt(TrendItem::heatScore).reversed())
                .thenComparing(TrendItem::publishedAt, Comparator.reverseOrder())
                .thenComparing(TrendItem::id)));
        groups.replaceAll(this::mixCreatorTiers);
        List<List<TrendItem>> socialGroups = SOCIAL_SOURCES.stream().map(groups::get).filter(java.util.Objects::nonNull).toList();
        List<List<TrendItem>> supplementalGroups = groups.entrySet().stream()
                .filter(entry -> !SOCIAL_SOURCES.contains(entry.getKey())).map(Map.Entry::getValue).toList();
        List<TrendItem> socialItems = roundRobin(socialGroups);
        List<TrendItem> supplementalItems = roundRobin(supplementalGroups);
        int supplementalLimit = socialItems.isEmpty()
                ? supplementalItems.size()
                : Math.min(supplementalItems.size(),
                        (socialItems.size() + SOCIAL_ITEMS_PER_SUPPLEMENTAL - 1) / SOCIAL_ITEMS_PER_SUPPLEMENTAL);
        List<TrendItem> ordered = new ArrayList<>(socialItems.size() + supplementalItems.size());
        int socialIndex = 0;
        int supplementalIndex = 0;
        while (socialIndex < socialItems.size() || supplementalIndex < supplementalLimit) {
            for (int count = 0; count < SOCIAL_ITEMS_PER_SUPPLEMENTAL && socialIndex < socialItems.size(); count++)
                ordered.add(socialItems.get(socialIndex++));
            if (supplementalIndex < supplementalLimit) ordered.add(supplementalItems.get(supplementalIndex++));
        }
        List<TrendItem> filtered = ordered.stream()
                .filter(i -> platform == null || platform.isBlank() || platform.equals(i.platform()))
                .filter(i -> topic == null || topic.isBlank() || i.title().contains(topic) || i.topicTags().stream().anyMatch(t -> t.contains(topic)))
                .limit(50).toList();
        // Only sources the application actually has are reported: a stored id that no longer maps to
        // an adapter (a renamed or removed feed) must not linger in the user-visible source list.
        Map<String, TrendSourceStatus> statusById = new HashMap<>();
        List<TrendSourceStatus> storedStatuses = repository.statuses();
        if (storedStatuses != null) {
            for (TrendSourceStatus status : storedStatuses) statusById.put(status.id(), status);
        }
        List<TrendSourceStatus> statuses = sourceStatuses(statusById, observedCounts, eligibleCounts,
                exclusionReasons);

        // A configured demo is deliberately a last resort for the unfiltered, all-source view.
        // A real item that survives the same period rules always wins, and selecting a real source
        // must never make the local demo look like that platform.
        if (filtered.isEmpty() && configuredDemoEnabled && isAllSourcesQuery(platform, topic)) {
            return configuredDemoFeed(period, statuses);
        }

        Instant fetched = filtered.stream().map(TrendItem::fetchedAt).max(Instant::compareTo).orElse(null);
        return new TrendFeed("multi-source", fetched, false, filtered, "来源内评分", period, styles(filtered), statuses,
                "统计采集范围内最近" + (period.equals("day") ? "24 小时" : "7 天")
                        + "发布或有已验证互动增长的内容；抖音/微博/小红书按粉丝数划分主流与小众博主，小众目标占已分层博主内容 "
                        + Math.round(nicheShare * 100) + "%，粉丝数缺失或显示精度不足的条目列为未分类补充。"
                        + (hotBoardsEnabled ? "匿名热榜只作为补充来源。" : "匿名热榜默认关闭。")
                        + "仅展示图文穿搭帖子与搭配思路，不收录视频与纯文字，排除杂志、红毯、时装周、秀场和明星时装大片。"
                        + "其他授权来源每 4 条博主内容最多补充 1 条；博主内容不足时按现有来源补齐。");
    }

    private List<TrendSourceStatus> sourceStatuses(Map<String, TrendSourceStatus> knownById,
            Map<String, Integer> observedCounts, Map<String, Integer> eligibleCounts,
            Map<String, List<String>> exclusionReasons) {
        List<TrendSourceStatus> statuses = new ArrayList<>();
        for (TrendSourceAdapter source : sources) {
            if ("editorial".equalsIgnoreCase(source.platform())) continue;
            String id = source.platform();
            TrendSourceStatus known = knownById.get(id);
            int observed = observedCounts.getOrDefault(id, 0);
            int collected = known == null ? observed : Math.max(known.itemCount(), observed);
            int eligible = eligibleCounts.getOrDefault(id, 0);
            List<String> reasons = exclusionReasons.get(id);
            String exclusionReason = reasons != null && !reasons.isEmpty()
                    ? mostCommonReason(reasons, collected)
                    : (eligible == 0 ? mostCommonReason(null, collected) : null);

            if (known == null) {
                if (collected == 0) {
                    statuses.add(placeholder(id));
                } else {
                    String message = exclusionReason == null ? "采集完成" : exclusionReason;
                    statuses.add(new TrendSourceStatus(id, null, null,
                            eligible == 0 ? "stale" : "ready", message, collected, eligible, exclusionReason));
                }
                continue;
            }

            String state = known.state();
            String message = known.message();
            if ("ready".equalsIgnoreCase(state) && collected > 0 && eligible == 0) {
                state = "stale";
                message = "已采集 " + collected + " 条，但当前时间范围内没有可展示内容";
            } else if (eligible > 0 && "stale".equalsIgnoreCase(state)) {
                state = "ready";
                message = "采集完成，当前窗口可展示 " + eligible + " 条";
            }
            statuses.add(new TrendSourceStatus(id, known.lastAttemptAt(), known.lastSuccessAt(),
                    state, message, collected, eligible, exclusionReason));
        }
        statuses.sort(Comparator.comparing(TrendSourceStatus::id));
        return statuses;
    }

    private static void noteExclusion(Map<String, List<String>> reasons, String sourceId, String reason) {
        if (sourceId == null || reason == null) return;
        reasons.computeIfAbsent(sourceId, ignored -> new ArrayList<>()).add(reason);
    }

    private static String mostCommonReason(List<String> reasons, int collected) {
        if (reasons == null || reasons.isEmpty()) {
            return collected > 0
                    ? "当前窗口内无可展示内容（可能已超过时间窗口或没有可验证的互动增长）"
                    : null;
        }
        return reasons.stream().collect(java.util.stream.Collectors.groupingBy(
                        reason -> reason, LinkedHashMap::new, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).orElse(null);
    }

    private static boolean isAllSourcesQuery(String platform, String topic) {
        return (platform == null || platform.isBlank()) && (topic == null || topic.isBlank());
    }

    private TrendFeed configuredDemoFeed(String period, List<TrendSourceStatus> statuses) {
        // Keep the five fixed samples available in both period views.  The period is still echoed
        // by TrendFeed and in the notice; unlike real data, the static sample is not re-dated to
        // the request time merely to satisfy a rolling cutoff.
        String window = period.equals("day") ? "最近24小时" : "最近7天";
        return new TrendFeed(CONFIGURED_DEMO_SOURCE, CONFIGURED_DEMO_CAPTURED_AT, true,
                CONFIGURED_DEMO_ITEMS, "配置演示固定热度", period, styles(CONFIGURED_DEMO_ITEMS), statuses,
                "当前为配置演示内容，非实时趋势；样本固定捕获于 " + CONFIGURED_DEMO_CAPTURED_AT
                        + "，不会写入趋势库，也不会计算或伪造互动增长。当前查询范围为" + window + "（演示样本按固定捕获时间展示）。");
    }

    private static List<TrendItem> configuredDemoItems() {
        Instant capture = CONFIGURED_DEMO_CAPTURED_AT;
        return List.of(
                configuredDemoItem("urban", "城市层次穿搭", List.of("通勤", "叠穿"), 92,
                        capture.minus(2, ChronoUnit.HOURS), "/assets/look-urban.jpg",
                        "城市日常层次搭配参考。"),
                configuredDemoItem("tailoring", "利落通勤剪裁", List.of("通勤", "简约"), 88,
                        capture.minus(5, ChronoUnit.HOURS), "/assets/look-tailoring.jpg",
                        "适合工作日的利落轮廓参考。"),
                configuredDemoItem("color", "低饱和配色", List.of("配色", "日常"), 84,
                        capture.minus(9, ChronoUnit.HOURS), "/assets/look-color.jpg",
                        "用克制配色组织日常穿搭。"),
                configuredDemoItem("commute-flatlay", "通勤衣橱组合", List.of("通勤", "衣橱"), 80,
                        capture.minus(1, ChronoUnit.DAYS), "/assets/look-commute-flatlay.png",
                        "通勤单品组合示意。"),
                configuredDemoItem("everyday-flatlay", "日常轻松搭配", List.of("日常", "轻松"), 76,
                        capture.minus(2, ChronoUnit.DAYS), "/assets/look-everyday-flatlay.png",
                        "日常出门的轻松搭配示意。"));
    }

    private static TrendItem configuredDemoItem(String suffix, String title, List<String> tags,
            int heatScore, Instant publishedAt, String imageUrl, String summary) {
        return new TrendItem("configured-demo:" + suffix, CONFIGURED_DEMO_SOURCE, title, tags, heatScore,
                publishedAt, CONFIGURED_DEMO_CAPTURED_AT, "/", true, imageUrl, summary,
                new TrendEvidence("配置演示", "image", List.of(imageUrl), null, null, null, null,
                        "配置演示固定热度", null, null, null, null, imageUrl));
    }

    private static long value(Long value) { return value == null ? 0 : value; }

    private static boolean hasVisualShare(TrendItem item) {
        if (item.imageUrl() != null && !item.imageUrl().isBlank()) return true;
        return item.evidence() != null && item.evidence().images() != null && !item.evidence().images().isEmpty();
    }

    private static long creatorReach(TrendItem item) {
        if (item.evidence() == null) return 0;
        if (item.evidence().authorFollowers() != null) return item.evidence().authorFollowers();
        String label = item.evidence().authorFollowersLabel();
        if (label == null || label.isBlank()) return 0;
        var matcher = java.util.regex.Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*(万|亿|w|W|千|k|K)\\s*$")
                .matcher(label);
        if (!matcher.matches()) return 0;
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(matcher.group(1));
            java.math.BigDecimal unit = switch (matcher.group(2).toLowerCase(java.util.Locale.ROOT)) {
                case "万", "w" -> java.math.BigDecimal.valueOf(10_000);
                case "亿" -> java.math.BigDecimal.valueOf(100_000_000);
                case "千", "k" -> java.math.BigDecimal.valueOf(1_000);
                default -> java.math.BigDecimal.ZERO;
            };
            return value.multiply(unit).min(java.math.BigDecimal.valueOf(Long.MAX_VALUE)).longValue();
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static List<TrendItem> roundRobin(List<List<TrendItem>> groups) {
        List<TrendItem> ordered = new ArrayList<>();
        int max = groups.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < max; i++) for (List<TrendItem> group : groups) if (i < group.size()) ordered.add(group.get(i));
        return ordered;
    }

    private String classifyCreator(Long followers, String followersLabel) {
        if (followers != null && followers > 0)
            return followers >= mainstreamFollowers ? "mainstream" : "niche";
        if (followersLabel == null || followersLabel.isBlank()) return "unclassified";
        var matcher = java.util.regex.Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*(万|亿|w|W|千|k|K)\\s*$")
                .matcher(followersLabel);
        if (!matcher.matches()) return "unclassified";
        try {
            java.math.BigDecimal display = new java.math.BigDecimal(matcher.group(1));
            java.math.BigDecimal unit = switch (matcher.group(2).toLowerCase(java.util.Locale.ROOT)) {
                case "万", "w" -> java.math.BigDecimal.valueOf(10_000);
                case "亿" -> java.math.BigDecimal.valueOf(100_000_000);
                case "千", "k" -> java.math.BigDecimal.valueOf(1_000);
                default -> throw new IllegalStateException("unsupported follower unit");
            };
            int decimals = Math.max(0, display.scale());
            java.math.BigDecimal halfStep = unit.movePointLeft(decimals)
                    .divide(java.math.BigDecimal.valueOf(2));
            java.math.BigDecimal displayedValue = display.multiply(unit);
            java.math.BigDecimal lowerBound = displayedValue.subtract(halfStep);
            java.math.BigDecimal upperBound = displayedValue.add(halfStep);
            java.math.BigDecimal threshold = java.math.BigDecimal.valueOf(mainstreamFollowers);
            if (lowerBound.compareTo(threshold) >= 0) return "mainstream";
            if (upperBound.compareTo(threshold) < 0) return "niche";
            return "unclassified";
        } catch (NumberFormatException exception) {
            return "unclassified";
        }
    }

    private List<TrendItem> mixCreatorTiers(String platform, List<TrendItem> ranked) {
        if (!SOCIAL_SOURCES.contains(platform)) return ranked;
        List<TrendItem> mainstream = new ArrayList<>();
        List<TrendItem> niche = new ArrayList<>();
        List<TrendItem> unclassified = new ArrayList<>();
        for (TrendItem item : ranked) {
            Long followers = item.evidence() == null ? null : item.evidence().authorFollowers();
            String followersLabel = item.evidence() == null ? null : item.evidence().authorFollowersLabel();
            String tier = classifyCreator(followers, followersLabel);
            if ("mainstream".equals(tier)) mainstream.add(item);
            else if ("niche".equals(tier)) niche.add(item);
            else unclassified.add(item);
        }
        List<TrendItem> mixed = new ArrayList<>(ranked.size());
        int mainIndex = 0;
        int nicheIndex = 0;
        while (mainIndex < mainstream.size() || nicheIndex < niche.size()) {
            double targetNicheCount = (mixed.size() + 1) * nicheShare;
            boolean chooseNiche = nicheIndex < niche.size()
                    && (mainIndex >= mainstream.size() || nicheIndex + 1 <= targetNicheCount);
            mixed.add(chooseNiche ? niche.get(nicheIndex++) : mainstream.get(mainIndex++));
        }
        mixed.addAll(unclassified);
        return mixed;
    }
    /** A source that has not been refreshed yet must still be visible, so users see what is not connected. */
    private static TrendSourceStatus placeholder(String id) {
        return SOCIAL_SOURCES.contains(id)
                ? new TrendSourceStatus(id, null, null, "unconfigured", "等待连接可用的数据源", 0)
                : new TrendSourceStatus(id, null, null, "pending", "等待首次采集", 0);
    }
    private static List<TrendStyle> styles(List<TrendItem> items) {
        Map<String, List<TrendItem>> grouped = new LinkedHashMap<>();
        for (TrendItem item : items) for (String tag : item.topicTags().stream().distinct().toList())
            grouped.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(item);
        return grouped.entrySet().stream().sorted(Comparator.<Map.Entry<String, List<TrendItem>>>comparingInt(e -> e.getValue().size()).reversed())
                .limit(6).map(e -> new TrendStyle(e.getKey(), e.getValue().size(),
                        e.getValue().stream().map(TrendItem::platform).distinct().toList(),
                        e.getValue().stream().map(TrendItem::imageUrl).filter(java.util.Objects::nonNull).findFirst().orElse(null),
                        "当前收录内容中有 " + e.getValue().size() + " 篇提及此风格，点击查看依据。")).toList();
    }
    public TrendItem reference(String id) {
        TrendItem item = repository.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "参考穿搭已下架或不存在，请重新选择"));
        if (TrendTopics.excludedShowOrCelebrity(item))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "参考穿搭已下架或不存在，请重新选择");
        return item;
    }
}
