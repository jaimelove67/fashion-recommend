package com.fashion.recommendation.trend;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TrendService {
    public TrendFeed currentFeed(String platform, String topic) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<TrendItem> items = List.of(
                new TrendItem("dy-urban-layering", "douyin", "轻机能通勤的层次感", List.of("通勤", "轻机能", "叠穿"), 92, now.minus(2, ChronoUnit.HOURS), now, "https://www.douyin.com/", false, "/assets/look-urban.jpg"),
                new TrendItem("dy-soft-tailoring", "douyin", "柔和剪裁回到日常衣橱", List.of("极简", "西装", "中性色"), 86, now.minus(4, ChronoUnit.HOURS), now, "https://www.douyin.com/", false, "/assets/look-tailoring.jpg"),
                new TrendItem("dy-color-pairing", "douyin", "低饱和蓝与暖白的夏日组合", List.of("配色", "夏日", "低饱和"), 78, now.minus(6, ChronoUnit.HOURS), now, "https://www.douyin.com/", false, "/assets/look-color.jpg")
        );
        List<TrendItem> filtered = items.stream()
                .filter(item -> platform == null || platform.isBlank() || item.platform().equalsIgnoreCase(platform))
                .filter(item -> topic == null || topic.isBlank() || item.topicTags().stream().anyMatch(tag -> tag.contains(topic)))
                .toList();
        return new TrendFeed("douyin", now, true, filtered);
    }
}
