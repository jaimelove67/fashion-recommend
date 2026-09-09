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
                    return new TrendFeed(adapter.platform(), fetchedAt, false, filter(fetchedItems, platform, topic),
                            adapter.scoreLabel());
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
                new TrendItem("dy-urban-layering", "douyin", "轻机能通勤的层次感", List.of("通勤", "轻机能", "叠穿"), 96, now.minus(2, ChronoUnit.HOURS), now, "https://www.douyin.com/", false, "/assets/look-urban.jpg"),
                new TrendItem("dy-soft-tailoring", "douyin", "柔和剪裁回到日常衣橱", List.of("极简", "西装", "中性色"), 93, now.minus(4, ChronoUnit.HOURS), now, "https://www.douyin.com/", false, "/assets/look-tailoring.jpg"),
                new TrendItem("dy-color-pairing", "douyin", "低饱和蓝与暖白的夏日组合", List.of("配色", "夏日", "低饱和"), 91, now.minus(6, ChronoUnit.HOURS), now, "https://www.douyin.com/", false, "/assets/look-color.jpg"),
                new TrendItem("xhs-linen-quiet", "xiaohongshu", "亚麻白衬衫的松弛留白", List.of("松弛感", "亚麻", "暖白"), 89, now.minus(8, ChronoUnit.HOURS), now, "https://www.xiaohongshu.com/", false, "/assets/look-color.jpg"),
                new TrendItem("wb-olive-city", "weibo", "橄榄绿把城市穿得更轻", List.of("城市休闲", "橄榄绿", "轻户外"), 87, now.minus(10, ChronoUnit.HOURS), now, "https://weibo.com/", false, "/assets/look-urban.jpg"),
                new TrendItem("xhs-silver-accessory", "xiaohongshu", "银色配饰提亮基础款", List.of("配饰", "基础款", "冷感"), 84, now.minus(12, ChronoUnit.HOURS), now, "https://www.xiaohongshu.com/", false, "/assets/look-tailoring.jpg"),
                new TrendItem("dy-denim-monochrome", "douyin", "同色系丹宁的干净轮廓", List.of("丹宁", "同色系", "简洁"), 82, now.minus(14, ChronoUnit.HOURS), now, "https://www.douyin.com/", false, "/assets/look-urban.jpg"),
                new TrendItem("wb-mist-blue-knit", "weibo", "雾蓝针织的轻薄层次", List.of("针织", "雾蓝", "换季"), 79, now.minus(16, ChronoUnit.HOURS), now, "https://weibo.com/", false, "/assets/look-color.jpg"),
                new TrendItem("xhs-black-loafer", "xiaohongshu", "黑色乐福鞋的利落收尾", List.of("鞋履", "通勤", "黑色"), 76, now.minus(18, ChronoUnit.HOURS), now, "https://www.xiaohongshu.com/", false, "/assets/look-urban.jpg"),
                new TrendItem("dy-soft-sport", "douyin", "柔软运动感进入周末衣橱", List.of("周末", "运动感", "舒适"), 73, now.minus(20, ChronoUnit.HOURS), now, "https://www.douyin.com/", false, "/assets/look-tailoring.jpg")
        );
        return new TrendFeed("douyin-development-sample", now, true, filter(items, platform, topic), "开发样本热度");
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
