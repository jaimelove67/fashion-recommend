package com.fashion.recommendation.trend;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TrendServiceTest {
    @Test void doesNotInventSamplesWhenAllSourcesFail() {
        TrendRepository repository = mock(TrendRepository.class);
        TrendSourceAdapter source = mock(TrendSourceAdapter.class);
        when(source.platform()).thenReturn("douyin");
        when(source.fetchPublicSnapshots()).thenThrow(new TrendSourceException("unavailable"));
        var service = new TrendService(List.of(source), repository);
        service.refresh();
        var feed = service.currentFeed(null, null);
        assertFalse(feed.demoMode());
        assertTrue(feed.items().isEmpty());
        verify(repository).status(eq("douyin"), any(), eq(false), eq(0), anyString());
    }

    @Test void anEmptyResultFromAHealthyBoardSourceStillCountsAsConnected() {
        TrendRepository repository = mock(TrendRepository.class);
        TrendSourceAdapter source = mock(TrendSourceAdapter.class);
        when(source.platform()).thenReturn("weibo");
        when(source.fetchPublicSnapshots()).thenReturn(List.of());
        when(source.emptyResultIsHealthy()).thenReturn(true);
        new TrendService(List.of(source), repository).refresh();
        verify(repository).status(eq("weibo"), any(), eq(true), eq(0), contains("没有穿搭相关内容"));
        verify(repository, never()).save(anyString(), any());
    }

    @Test void filtersWindowsAndDoesNotConfuseOldViralPostsWithNewTrends() {
        TrendRepository repository = mock(TrendRepository.class);
        var fresh = item("new", "douyin", 2);
        var week = item("week", "weibo", 60);
        var old = item("old", "douyin", 400);
        when(repository.since(any())).thenReturn(List.of(fresh, week, old));
        var service = new TrendService(List.of(), repository);
        assertEquals(List.of("new"), service.currentFeed(null, null, "day").items().stream().map(TrendItem::id).toList());
        assertEquals(2, service.currentFeed(null, null, "week").items().size());
        assertTrue(service.currentFeed("xiaohongshu", null).items().isEmpty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.currentFeed(null, null, "year"));
    }

    @Test void combinesPlatformsInsteadOfReturningTheFirstOneOnly() {
        TrendRepository repository = mock(TrendRepository.class);
        when(repository.since(any())).thenReturn(List.of(item("a", "douyin", 1), item("b", "douyin", 2), item("c", "weibo", 3)));
        var feed = new TrendService(List.of(), repository).currentFeed(null, null);
        assertEquals(List.of("douyin", "weibo", "douyin"), feed.items().stream().map(TrendItem::platform).toList());
        assertEquals(3, feed.styles().get(0).contentCount());
        assertEquals(2, feed.styles().get(0).platforms().size());
    }
    private TrendItem item(String id, String platform, int hoursAgo) {
        return new TrendItem(id, platform, "通勤穿搭", List.of("通勤"), 50,
                Instant.now().minus(hoursAgo, ChronoUnit.HOURS), Instant.now(), "https://example.com/" + id, false, null);
    }
}
