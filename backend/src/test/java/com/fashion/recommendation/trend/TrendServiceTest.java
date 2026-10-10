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

    @Test void configuredDemoIsOffByDefaultAndOnlyEnabledAsAnExplicitFallback() {
        TrendRepository repository = mock(TrendRepository.class);
        when(repository.since(any())).thenReturn(List.of());
        when(repository.statuses()).thenReturn(List.of());

        TrendFeed disabled = new TrendService(List.of(), repository).currentFeed(null, null);
        TrendFeed enabled = new TrendService(List.of(), repository, true).currentFeed(null, null);

        assertFalse(disabled.demoMode());
        assertTrue(disabled.items().isEmpty());
        assertTrue(enabled.demoMode());
        assertEquals("configured-demo", enabled.primarySource());
        assertEquals(5, enabled.items().size());
        assertTrue(enabled.notice().contains("非实时"));
        assertTrue(enabled.items().stream().allMatch(item -> "configured-demo".equals(item.platform())));
        assertTrue(enabled.items().stream().allMatch(item -> item.imageUrl().equals(item.evidence().fullBodyImageUrl())));
        assertTrue(enabled.items().stream().allMatch(item -> item.evidence().images().contains(item.imageUrl())));
        verify(repository, never()).save(anyString(), any());
    }

    @Test void realEligibleItemsWinOverConfiguredDemo() {
        TrendRepository repository = mock(TrendRepository.class);
        TrendItem real = item("real", "douyin", 1);
        when(repository.since(any())).thenReturn(List.of(real));
        when(repository.statuses()).thenReturn(List.of());

        TrendFeed feed = new TrendService(List.of(), repository, true).currentFeed(null, null);

        assertFalse(feed.demoMode());
        assertEquals(List.of("real"), feed.items().stream().map(TrendItem::id).toList());
    }

    @Test void platformSelectionNeverReturnsConfiguredDemo() {
        TrendRepository repository = mock(TrendRepository.class);
        when(repository.since(any())).thenReturn(List.of());
        when(repository.statuses()).thenReturn(List.of());

        TrendFeed feed = new TrendService(List.of(), repository, true).currentFeed("douyin", null);

        assertFalse(feed.demoMode());
        assertTrue(feed.items().isEmpty());
    }

    @Test void configuredDemoReferenceStillRequiresAStoredRepositoryItem() {
        TrendRepository repository = mock(TrendRepository.class);
        when(repository.find("configured-demo:urban")).thenReturn(java.util.Optional.empty());

        var service = new TrendService(List.of(), repository, true);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.reference("configured-demo:urban"));
        verify(repository).find("configured-demo:urban");
    }

    @Test void sourceStatusReportsCollectedEligibleAndExclusionReasonForCurrentWindow() {
        TrendRepository repository = mock(TrendRepository.class);
        Instant now = Instant.now();
        TrendItem eligible = item("eligible", "douyin", 1);
        TrendItem excluded = new TrendItem("excluded", "douyin", "旧通勤穿搭", List.of("通勤"), 50,
                now.minus(10, ChronoUnit.DAYS), now, "https://example.com/excluded", false, "https://example.com/excluded.jpg");
        when(repository.since(any())).thenReturn(List.of(eligible, excluded));
        when(repository.statuses()).thenReturn(List.of(new TrendSourceStatus("douyin", now.minus(1, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.HOURS), "ready", "采集完成", 2)));

        TrendSourceStatus status = new TrendService(List.of(source("douyin")), repository)
                .currentFeed(null, null).sources().stream()
                .filter(candidate -> "douyin".equals(candidate.id())).findFirst().orElseThrow();

        assertEquals(2, status.itemCount());
        assertEquals(1, status.eligibleCount());
        assertEquals("ready", status.state());
        assertTrue(status.exclusionReason().contains("时间窗口"));
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

    @Test void excludesRedCarpetAndCelebrityEditorialItemsFromStoredResults() {
        TrendRepository repository = mock(TrendRepository.class);
        TrendItem excluded = new TrendItem("red-carpet", "weibo", "艾美奖红毯礼服造型",
                List.of("红毯造型", "礼服"), 99, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(),
                "https://example.com/red-carpet", false, "https://example.com/red-carpet.jpg", "明星红毯造型", null);
        when(repository.since(any())).thenReturn(List.of(excluded, item("creator", "douyin", 1)));

        var feed = new TrendService(List.of(), repository).currentFeed(null, null);

        assertEquals(List.of("creator"), feed.items().stream().map(TrendItem::id).toList());
    }

    @Test void excludesTextOnlyEntriesWhileKeepingImagePosts() {
        TrendRepository repository = mock(TrendRepository.class);
        TrendItem textOnly = new TrendItem("text-only", "douyin", "通勤穿搭", List.of("通勤"), 50, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), "https://example.com/text-only", false, null);
        TrendItem visual = new TrendItem("visual", "douyin", "秋冬通勤穿搭分享",
                List.of("通勤", "叠穿"), 50, Instant.now().minus(2, ChronoUnit.HOURS), Instant.now(),
                "https://example.com/visual", false, "https://example.com/visual.jpg", null);
        when(repository.since(any())).thenReturn(List.of(textOnly, visual));

        var feed = new TrendService(List.of(), repository).currentFeed(null, null);

        assertEquals(List.of("visual"), feed.items().stream().map(TrendItem::id).toList());
    }

    @Test void prioritizesHigherReachCreatorsWithinTheSameTier() {
        TrendRepository repository = mock(TrendRepository.class);
        var lowerReach = itemWithFollowers("lower-reach", "14.4万");
        var higherReach = itemWithFollowers("higher-reach", "533.1万");
        when(repository.since(any())).thenReturn(List.of(lowerReach, higherReach));

        var feed = new TrendService(List.of(), repository).currentFeed(null, null);

        assertEquals(List.of("higher-reach", "lower-reach"), feed.items().stream().map(TrendItem::id).toList());
    }

    @Test void keepsReviewedFullBodyImageWhenBuildingThePublicFeed() {
        TrendRepository repository = mock(TrendRepository.class);
        String fullBody = "https://images.example/full-body.jpg";
        var evidence = new TrendEvidence("creator", "image", List.of(fullBody), null, null, null, null,
                "来源评分", null, null, null, null, fullBody);
        var item = new TrendItem("look", "weibo", "秋季日常穿搭", List.of("通勤"), 12,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), "https://weibo.com/detail/look",
                false, "https://images.example/cover.jpg", null, evidence);
        when(repository.since(any())).thenReturn(List.of(item));

        var feed = new TrendService(List.of(), repository).currentFeed(null, null);

        assertEquals(fullBody, feed.items().get(0).evidence().fullBodyImageUrl());
    }

    @Test void classifiesRoundedFollowerRangesConservatively() {
        TrendRepository repository = mock(TrendRepository.class);
        when(repository.since(any())).thenReturn(List.of(
                itemWithFollowers("large", "23.2万"),
                itemWithFollowers("small", "1.2万"),
                itemWithFollowers("at-threshold", "10.0万")));

        var feed = new TrendService(List.of(), repository).currentFeed(null, null);

        var tiers = feed.items().stream().collect(java.util.stream.Collectors.toMap(
                item -> item.title(), item -> item.evidence().creatorTier()));
        assertEquals("mainstream", tiers.get("large"));
        assertEquals("niche", tiers.get("small"));
        assertEquals("unclassified", tiers.get("at-threshold"));
        assertTrue(feed.items().stream().allMatch(item -> item.evidence().authorFollowers() == null));
    }

    @Test void neverExposesMagazinePublisherItemsEvenWhenStored() {
        TrendRepository repository = mock(TrendRepository.class);
        var social = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> itemWithFollowers("social-" + index, "23.2万")).toList();
        var editorial = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> item("editorial-" + index, "editorial", index)).toList();
        when(repository.since(any())).thenReturn(java.util.stream.Stream.concat(social.stream(), editorial.stream()).toList());

        var feed = new TrendService(List.of(), repository).currentFeed(null, null);

        assertEquals(8, feed.items().stream().filter(item -> "douyin".equals(item.platform())).count());
        assertEquals(0, feed.items().stream().filter(item -> "editorial".equals(item.platform())).count());
    }

    @Test void doesNotRefreshPublisherFeeds() {
        TrendRepository repository = mock(TrendRepository.class);
        TrendSourceAdapter editorial = mock(TrendSourceAdapter.class);
        when(editorial.platform()).thenReturn("editorial");

        new TrendService(List.of(editorial), repository).refresh();

        verify(editorial, never()).fetchPublicSnapshots();
        verify(repository, never()).save(anyString(), any());
    }

    @Test void acceptsImagePostsAcrossPlatformsButExcludesVideosAndShows() {
        TrendRepository repository = mock(TrendRepository.class);
        var image = item("xhs-image", "xiaohongshu", 1);
        var other = item("other-image", "other-platform", 1);
        var video = new TrendItem("video", "weibo", "通勤穿搭视频", List.of("通勤"), 50,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), "https://example.com/video", false,
                "https://example.com/video-cover.jpg", null,
                new TrendEvidence("author", "video", List.of("https://example.com/video-cover.jpg"),
                        null, null, null, null, "source", null, null, null, null));
        var show = new TrendItem("show", "xiaohongshu", "巴黎时装周穿搭", List.of("穿搭"), 50,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), "https://example.com/show", false,
                "https://example.com/show.jpg");
        when(repository.since(any())).thenReturn(List.of(image, other, video, show));
        var feed = new TrendService(List.of(), repository).currentFeed(null, null, "day");
        assertEquals(java.util.Set.of("xhs-image", "other-image"), feed.items().stream()
                .map(TrendItem::id).collect(java.util.stream.Collectors.toSet()));
    }

    private TrendItem itemWithFollowers(String title, String followersLabel) {
        var evidence = new TrendEvidence("author", "image", List.of("https://example.com/" + title + ".jpg"), null, null, null, null,
                "source", null, null, followersLabel, null);
        return new TrendItem(title, "douyin", title, List.of("秋季穿搭"), 0,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), "https://example.com/" + title,
                false, "https://example.com/" + title + ".jpg", null, evidence);
    }

    private TrendItem item(String id, String platform, int hoursAgo) {
        return new TrendItem(id, platform, "通勤穿搭", List.of("通勤"), 50,
                Instant.now().minus(hoursAgo, ChronoUnit.HOURS), Instant.now(), "https://example.com/" + id, false, "https://example.com/" + id + ".jpg");
    }

    private TrendSourceAdapter source(String platform) {
        TrendSourceAdapter source = mock(TrendSourceAdapter.class);
        when(source.platform()).thenReturn(platform);
        return source;
    }
}
