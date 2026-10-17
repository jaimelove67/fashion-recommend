package com.fashion.recommendation.trend;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrendDailyFeedTest {
    @Test void combinesPlatformsIntoOneDailySelectionBeforeSourceFiltering() {
        var repository = mock(TrendRepository.class);
        when(repository.since(any())).thenReturn(List.of(
                post("m1", "weibo", List.of("男装穿搭"), 1),
                post("m2", "xiaohongshu", List.of("男装穿搭"), 2),
                post("m3", "douyin", List.of("男装穿搭"), 3),
                post("w1", "weibo", List.of("女装穿搭"), 1),
                post("w2", "douyin", List.of("女装穿搭"), 2),
                post("n1", "xiaohongshu", List.of("小众穿搭", "哥特风"), 1),
                post("unknown", "weibo", List.of("通勤"), 1)));
        var service = new TrendService(List.of(), repository);
        var all = service.currentFeed(null, null, "day");
        assertEquals(5, all.items().size());
        assertEquals(2, all.dailySelection().menswear());
        assertEquals(2, all.dailySelection().womenswear());
        assertEquals(1, all.dailySelection().niche());
        assertFalse(all.items().stream().anyMatch(item -> item.id().equals("unknown")));
        assertTrue(all.items().stream().map(TrendItem::platform).distinct().count() > 1);
        var onlyWeibo = service.currentFeed("weibo", null, "day");
        assertEquals(all.items().stream().filter(item -> item.platform().equals("weibo")).map(TrendItem::id).toList(),
                onlyWeibo.items().stream().map(TrendItem::id).toList());
        assertEquals(all.dailySelection(), onlyWeibo.dailySelection());
        assertEquals(all.items().stream().filter(item -> item.title().contains("m1")).map(TrendItem::id).toList(),
                service.currentFeed(null, "m1", "day").items().stream().map(TrendItem::id).toList());
    }

    @Test void otherPlatformsCanFillDailyCategoriesBeforeTheWeeklySupplementalLimit() {
        var repository = mock(TrendRepository.class);
        when(repository.since(any())).thenReturn(List.of(
                post("m1", "weibo", List.of("男装穿搭"), 1),
                post("m2", "weibo", List.of("男装穿搭"), 2),
                post("w1", "other-platform", List.of("女装穿搭"), 1),
                post("w2", "other-platform", List.of("女装穿搭"), 2),
                post("n1", "other-platform", List.of("小众穿搭", "哥特风"), 1)));
        var service = new TrendService(List.of(), repository);
        var daily = service.currentFeed(null, null, "day");
        assertEquals(5, daily.items().size());
        assertEquals(0, daily.dailySelection().missing().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(3, daily.items().stream().filter(item -> item.platform().equals("other-platform")).count());
        assertEquals(3, service.currentFeed(null, null, "week").items().size());
    }

    @Test void anOldPostWithInteractionGrowthCannotFillADailySlot() {
        var repository = mock(TrendRepository.class);
        var old = post("old", "weibo", List.of("男装穿搭"), 48);
        var fresh = post("fresh", "weibo", List.of("女装穿搭"), 1);
        when(repository.since(any())).thenReturn(List.of(old, fresh));
        when(repository.growth(any(), any())).thenReturn(100L);
        var service = new TrendService(List.of(), repository);
        assertEquals(List.of("fresh"), service.currentFeed(null, null, "day").items().stream().map(TrendItem::id).toList());
        var week = service.currentFeed(null, null, "week");
        assertEquals(2, week.items().size());
        assertNull(week.dailySelection());
    }

    @Test void unclassifiedRealCandidatesDoNotTriggerAnOldDemoToFillMissingSlots() {
        var repository = mock(TrendRepository.class);
        when(repository.since(any())).thenReturn(List.of(post("unknown", "weibo", List.of("穿搭"), 1)));
        var feed = new TrendService(List.of(), repository, true).currentFeed(null, null, "day");
        assertFalse(feed.demoMode());
        assertTrue(feed.items().isEmpty());
        assertEquals(5, feed.dailySelection().missing().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test void candidateCountsAreNotConfusedWithTheFiveSelectedOutfits() {
        var repository = mock(TrendRepository.class);
        var source = mock(TrendSourceAdapter.class);
        when(source.platform()).thenReturn("weibo");
        when(repository.since(any())).thenReturn(List.of(
                post("m1", "weibo", List.of("男装穿搭"), 1), post("m2", "weibo", List.of("男装穿搭"), 2),
                post("m3", "weibo", List.of("男装穿搭"), 3), post("w1", "weibo", List.of("女装穿搭"), 1),
                post("w2", "weibo", List.of("女装穿搭"), 2), post("w3", "weibo", List.of("女装穿搭"), 3),
                post("n1", "weibo", List.of("小众穿搭", "蒸汽朋克"), 1)));
        var feed = new TrendService(List.of(source), repository).currentFeed(null, null, "day");
        assertEquals(5, feed.items().size());
        assertEquals(7, feed.sources().get(0).eligibleCount());
        assertEquals(7, feed.sources().get(0).itemCount());
    }

    private TrendItem post(String id, String platform, List<String> tags, int hoursAgo) {
        Instant now = Instant.now();
        return new TrendItem(id, platform, id + " 日常穿搭", tags, 50,
                now.minus(hoursAgo, ChronoUnit.HOURS), now, "https://example.com/post/" + id, false,
                "https://images.example/" + id + ".jpg", null,
                new TrendEvidence("creator", "image", List.of("https://images.example/" + id + ".jpg"),
                        null, null, null, null, "来源评分", null));
    }
}
