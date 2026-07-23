package com.fashion.recommendation.trend;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TrendService {
    private static final Logger log = LoggerFactory.getLogger(TrendService.class);
    private final List<TrendSourceAdapter> sourceAdapters;

    public TrendService(List<TrendSourceAdapter> sourceAdapters) {
        this.sourceAdapters = sourceAdapters;
    }

    public TrendFeed currentFeed(String platform, String topic) {
        for (TrendSourceAdapter adapter : sourceAdapters) {
            if (platform != null && !platform.isBlank() && !adapter.platform().equalsIgnoreCase(platform)) {
                continue;
            }
            try {
                List<TrendItem> fetchedItems = adapter.fetchPublicSnapshots();
                if (!fetchedItems.isEmpty()) {
                    Instant fetchedAt = fetchedItems.stream().map(TrendItem::fetchedAt).max(Instant::compareTo)
                            .orElseGet(Instant::now);
                    return new TrendFeed(adapter.platform(), fetchedAt, false, filter(fetchedItems, platform, topic));
                }
            } catch (RuntimeException exception) {
                log.warn("Trend source failed; using development samples, source={}, reason={}",
                        adapter.platform(), exception.getMessage());
            }
        }
        return developmentFeed(platform, topic);
    }

    private TrendFeed developmentFeed(String platform, String topic) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<TrendItem> items = List.of(
                new TrendItem("dy-urban-layering", "douyin", "轻机能通勤的层次感", List.of("通勤", "轻机能", "叠穿"), 92, now.minus(2, ChronoUnit.HOURS), now, "https://www.douyin.com/", false, "/assets/look-urban.jpg"),
                new TrendItem("dy-soft-tailoring", "douyin", "柔和剪裁回到日常衣橱", List.of("极简", "西装", "中性色"), 86, now.minus(4, ChronoUnit.HOURS), now, "https://www.douyin.com/", false, "/assets/look-tailoring.jpg"),
                new TrendItem("dy-color-pairing", "douyin", "低饱和蓝与暖白的夏日组合", List.of("配色", "夏日", "低饱和"), 78, now.minus(6, ChronoUnit.HOURS), now, "https://www.douyin.com/", false, "/assets/look-color.jpg")
        );
        return new TrendFeed("douyin-development-sample", now, true, filter(items, platform, topic));
    }

    private static List<TrendItem> filter(List<TrendItem> items, String platform, String topic) {
        return items.stream()
                .filter(item -> platform == null || platform.isBlank() || item.platform().equalsIgnoreCase(platform))
                .filter(item -> topic == null || topic.isBlank()
                        || item.title().contains(topic)
                        || item.topicTags().stream().anyMatch(tag -> tag.contains(topic)))
                .toList();
    }
}
