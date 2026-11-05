package com.fashion.recommendation.trend;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrendHistoryFallbackTest {
    @Test void emptyRecentFeedShowsApprovedStoredPostsWithoutChangingDatesOrDailyQuota() {
        var repository = mock(TrendRepository.class);
        var old = post("old", Instant.now().minusSeconds(20 * 86400));
        when(repository.since(any())).thenReturn(List.of());
        when(repository.approvedHistory()).thenReturn(List.of(old));
        var service = new TrendService(List.of(), repository, true);
        for (String period : List.of("day", "week")) {
            var feed = service.currentFeed(null, null, period);
            assertTrue(feed.historicalFallback());
            assertFalse(feed.demoMode());
            assertNull(feed.dailySelection());
            assertEquals(old.publishedAt(), feed.items().get(0).publishedAt());
            assertEquals(old.fetchedAt(), feed.items().get(0).fetchedAt());
            assertEquals(old.sourceUrl(), feed.items().get(0).sourceUrl());
            assertTrue(feed.items().get(0).stale());
        }
        assertTrue(service.currentFeed("douyin", null, "day").items().isEmpty());
        assertTrue(service.currentFeed(null, "不存在的主题", "day").items().isEmpty());
    }

    @Test void currentEligibleOutfitsWinAndHistoryCannotBypassImageContentRules() {
        var repository = mock(TrendRepository.class);
        var fresh = post("fresh", Instant.now().minusSeconds(600));
        when(repository.since(any())).thenReturn(List.of(fresh));
        var service = new TrendService(List.of(), repository);
        assertFalse(service.currentFeed(null, null, "day").historicalFallback());
        verify(repository, never()).approvedHistory();
        when(repository.since(any())).thenReturn(List.of());
        var old = post("old", Instant.now().minusSeconds(20 * 86400));
        var video = new TrendItem("video", "weibo", "通勤穿搭", List.of("男装穿搭"), 0,
                old.publishedAt(), old.fetchedAt(), old.sourceUrl(), false, old.imageUrl(), null,
                new TrendEvidence("author", "video", List.of(old.imageUrl()), null, null, null, null, "来源评分", null));
        when(repository.approvedHistory()).thenReturn(List.of(video));
        assertTrue(service.currentFeed(null, null, "day").items().isEmpty());
    }

    private TrendItem post(String id, Instant date) {
        return new TrendItem(id, "weibo", "通勤穿搭 " + id, List.of("男装穿搭"), 0, date, date,
                "https://weibo.com/123456/abc123", false, "https://images.example/" + id + ".jpg", null,
                new TrendEvidence("author", "image", List.of("https://images.example/" + id + ".jpg"), null, null, null, null, "来源评分", null));
    }
}
