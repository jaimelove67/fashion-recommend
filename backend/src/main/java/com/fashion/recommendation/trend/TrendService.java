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
    private static final List<String> SOCIAL_SOURCES = List.of("douyin", "weibo");
    private static final String EXCLUDED_PLATFORM = "xiaohongshu";
    private static final int SOCIAL_ITEMS_PER_SUPPLEMENTAL = 4;
    private final List<TrendSourceAdapter> sources;
    private final TrendRepository repository;
    private final boolean hotBoardsEnabled;

    @Autowired
    public TrendService(List<TrendSourceAdapter> sources, TrendRepository repository,
            @Value("${app.trends.mainstream-followers:100000}") long mainstreamFollowers,
            @Value("${app.trends.niche-share:0.25}") double nicheShare,
            @Value("${app.trends.hot-boards-enabled:false}") boolean hotBoardsEnabled) {
        if (mainstreamFollowers < 1) throw new IllegalArgumentException("mainstream followers must be positive");
        if (!Double.isFinite(nicheShare) || nicheShare < 0 || nicheShare > 1)
            throw new IllegalArgumentException("niche share must be between 0 and 1");
        this.sources = sources;
        this.repository = repository;
        this.mainstreamFollowers = mainstreamFollowers;
        this.nicheShare = nicheShare;
        this.hotBoardsEnabled = hotBoardsEnabled;
    }

    public TrendService(List<TrendSourceAdapter> sources, TrendRepository repository) {
        this(sources, repository, 100_000, 0.25, false);
    }

    private final long mainstreamFollowers;
    private final double nicheShare;

    @Scheduled(initialDelayString = "${app.trends.initial-delay:10000}", fixedDelayString = "${app.trends.refresh-interval:21600000}")
    public synchronized void refresh() {
        for (TrendSourceAdapter source : sources) {
            if (EXCLUDED_PLATFORM.equalsIgnoreCase(source.platform())) continue;
            Instant now = Instant.now();
            try {
                List<TrendItem> items = source.fetchPublicSnapshots();
                List<TrendItem> visibleItems = items.stream()
                        .filter(item -> !EXCLUDED_PLATFORM.equalsIgnoreCase(item.platform()))
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
        for (TrendItem item : stored) {
            if (EXCLUDED_PLATFORM.equalsIgnoreCase(item.platform())) continue;
            if (TrendTopics.excludedShowOrCelebrity(item)) continue;
            if (item.publishedAt().isAfter(now) || item.fetchedAt().isAfter(now.plusSeconds(60))) continue;
            TrendEvidence e = item.evidence();
            if (!hotBoardsEnabled && e != null && "board".equalsIgnoreCase(e.mediaType())) continue;
            Long growth = repository.growth(item, cutoff);
            if (item.publishedAt().isBefore(cutoff) && (growth == null || growth <= 0)) continue;
            int score = item.heatScore();
            String label = e == null ? "来源评分" : e.scoreLabel();
            if (e != null && e.hasCounters()) {
                double engagement = value(e.likes()) + 2d * value(e.favorites()) + 2d * value(e.comments()) + 3d * value(e.reposts());
                double hours = Math.max(0, Duration.between(item.publishedAt(), now).toHours());
                score = (int) Math.min(100, Math.round(12 * Math.log1p(engagement) / (1 + hours / 168)));
                label = "平台内互动评分";
            }
            boolean stale = item.stale() || item.fetchedAt().isBefore(now.minus(12, ChronoUnit.HOURS));
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
        // Rank within platforms, then prioritize creator posts while retaining a small editorial supplement.
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
        var filtered = ordered.stream()
                .filter(i -> platform == null || platform.isBlank() || platform.equals(i.platform()))
                .filter(i -> topic == null || topic.isBlank() || i.title().contains(topic) || i.topicTags().stream().anyMatch(t -> t.contains(topic)))
                .limit(50).toList();
        // Only sources the application actually has are reported: a stored id that no longer maps to
        // an adapter (a renamed or removed feed) must not linger in the user-visible source list.
        Map<String, TrendSourceStatus> statusById = new HashMap<>();
        for (TrendSourceStatus status : repository.statuses()) statusById.put(status.id(), status);
        List<TrendSourceStatus> statuses = new ArrayList<>();
        for (TrendSourceAdapter source : sources) {
            TrendSourceStatus known = statusById.get(source.platform());
            statuses.add(known == null ? placeholder(source.platform()) : known);
        }
        statuses.sort(Comparator.comparing(TrendSourceStatus::id));
        Instant fetched = filtered.stream().map(TrendItem::fetchedAt).max(Instant::compareTo).orElse(null);
        return new TrendFeed("multi-source", fetched, false, filtered, "来源内评分", period, styles(filtered), statuses,
                "统计采集范围内最近" + (period.equals("day") ? "24 小时" : "7 天")
                        + "发布或有已验证互动增长的内容；抖音/微博按粉丝数划分主流与小众博主，小众目标占已分层博主内容 "
                        + Math.round(nicheShare * 100) + "%，粉丝数缺失或显示精度不足的条目列为未分类补充。"
                        + (hotBoardsEnabled ? "匿名热榜只作为补充来源。" : "匿名热榜默认关闭。")
                        + "优先展示有图的博主穿搭分享与搭配思路，排除红毯、时装周、秀场和明星时装大片。"
                        + "博主内容充足时，编辑文章与其他授权来源每 4 条最多补充 1 条；博主内容不足时按现有来源补齐。");
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
