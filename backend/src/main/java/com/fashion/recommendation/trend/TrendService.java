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
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrendService {
    private final List<TrendSourceAdapter> sources;
    private final TrendRepository repository;
    public TrendService(List<TrendSourceAdapter> sources, TrendRepository repository) {
        this.sources = sources;
        this.repository = repository;
    }
    @Scheduled(initialDelayString = "${app.trends.initial-delay:10000}", fixedDelayString = "${app.trends.refresh-interval:21600000}")
    public synchronized void refresh() {
        for (TrendSourceAdapter source : sources) {
            Instant now = Instant.now();
            try {
                List<TrendItem> items = source.fetchPublicSnapshots();
                for (TrendItem item : items) repository.save(source.platform(), normalize(item, source.scoreLabel()));
                repository.status(source.platform(), now, !items.isEmpty() || source.emptyResultIsHealthy(), items.size(),
                        items.isEmpty()
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
        for (TrendItem item : repository.since(cutoff)) {
            if (item.publishedAt().isAfter(now) || item.fetchedAt().isAfter(now.plusSeconds(60))) continue;
            Long growth = repository.growth(item, cutoff);
            if (item.publishedAt().isBefore(cutoff) && (growth == null || growth <= 0)) continue;
            TrendEvidence e = item.evidence();
            int score = item.heatScore();
            String label = e == null ? "来源评分" : e.scoreLabel();
            if (e != null && e.hasCounters()) {
                double engagement = value(e.likes()) + 2d * value(e.favorites()) + 2d * value(e.comments()) + 3d * value(e.reposts());
                double hours = Math.max(0, Duration.between(item.publishedAt(), now).toHours());
                score = (int) Math.min(100, Math.round(12 * Math.log1p(engagement) / (1 + hours / 168)));
                label = "平台内互动评分";
            }
            boolean stale = item.stale() || item.fetchedAt().isBefore(now.minus(12, ChronoUnit.HOURS));
            scored.add(new TrendItem(item.id(), item.platform(), item.title(), item.topicTags(), score,
                    item.publishedAt(), item.fetchedAt(), item.sourceUrl(), stale, item.imageUrl(), item.summary(),
                    e == null ? null : e.scored(label, growth)));
        }
        // Rank within platforms; round-robin combines their independent lists.
        Map<String, List<TrendItem>> groups = new LinkedHashMap<>();
        for (TrendItem item : scored) groups.computeIfAbsent(item.platform(), ignored -> new ArrayList<>()).add(item);
        groups.values().forEach(items -> items.sort(Comparator.comparingInt(TrendItem::heatScore).reversed()
                .thenComparing(TrendItem::publishedAt, Comparator.reverseOrder()).thenComparing(TrendItem::id)));
        List<TrendItem> ordered = new ArrayList<>();
        int max = groups.values().stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < max; i++) for (List<TrendItem> group : groups.values()) if (i < group.size()) ordered.add(group.get(i));
        var filtered = ordered.stream()
                .filter(i -> platform == null || platform.isBlank() || platform.equals(i.platform()))
                .filter(i -> topic == null || topic.isBlank() || i.title().contains(topic) || i.topicTags().stream().anyMatch(t -> t.contains(topic)))
                .limit(50).toList();
        // Only sources the application actually has are reported: a stored id that no longer maps to
        // an adapter (a renamed or removed feed) must not linger in the user-visible source list.
        Map<String, TrendSourceStatus> stored = new HashMap<>();
        for (TrendSourceStatus status : repository.statuses()) stored.put(status.id(), status);
        List<TrendSourceStatus> statuses = new ArrayList<>();
        for (TrendSourceAdapter source : sources) {
            TrendSourceStatus known = stored.get(source.platform());
            statuses.add(known == null ? placeholder(source.platform()) : known);
        }
        statuses.sort(Comparator.comparing(TrendSourceStatus::id));
        Instant fetched = filtered.stream().map(TrendItem::fetchedAt).max(Instant::compareTo).orElse(null);
        return new TrendFeed("multi-source", fetched, false, filtered, "来源内评分", period, styles(filtered), statuses,
                "统计采集范围内最近" + (period.equals("day") ? "24 小时" : "7 天")
                        + "发布或有已验证互动增长的内容；跨平台交替展示。编辑文章按发布时间排列，不代表平台热榜。风格按内容标签归纳。");
    }
    private static long value(Long value) { return value == null ? 0 : value; }
    private static final List<String> SOCIAL_SOURCES = List.of("douyin", "xiaohongshu", "weibo");
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
        return repository.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "参考穿搭已下架或不存在，请重新选择"));
    }
}
