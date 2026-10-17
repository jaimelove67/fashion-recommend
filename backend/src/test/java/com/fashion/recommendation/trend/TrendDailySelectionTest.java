package com.fashion.recommendation.trend;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendDailySelectionTest {
    @Test
    void fillsExclusiveQuotasInExistingRankOrder() {
        var result = TrendDailySelection.select(List.of(
                item("w1", "女装穿搭", List.of()), item("m1", "男装穿搭", List.of()),
                item("n1", "哥特风女装穿搭", List.of("小众穿搭")),
                item("m2", "男士穿搭", List.of()), item("m3", "男生穿搭", List.of()),
                item("w2", "日常", List.of("女装穿搭")), item("n2", "蒸汽朋克穿搭", List.of())));
        assertEquals(List.of("w1", "m1", "n1", "m2", "w2"), result.items().stream().map(TrendItem::id).toList());
        assertEquals(5, result.summary().target());
        assertEquals(2, result.summary().menswear());
        assertEquals(2, result.summary().womenswear());
        assertEquals(1, result.summary().niche());
        assertEquals(0, result.summary().missing().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals("niche", result.summary().assignments().get(2).category());
        assertTrue(result.summary().assignments().get(2).reason().contains("哥特风"));
    }

    @Test
    void reservesNicheSlotBeforeHigherRankedMainstreamRepostUsesItsPhoto() {
        var mainstreamRepost = withImage(item("repost", "男装穿搭", List.of()), "https://images.example/shared.jpg");
        var nicheOriginal = withImage(item("original", "哥特风穿搭", List.of()), "https://images.example/shared.jpg");
        var result = TrendDailySelection.select(List.of(mainstreamRepost, nicheOriginal,
                item("m1", "男士穿搭", List.of()), item("m2", "男装穿搭", List.of()),
                item("w1", "女装穿搭", List.of()), item("w2", "女士搭配", List.of())));
        assertEquals(List.of("original", "m1", "m2", "w1", "w2"), result.items().stream().map(TrendItem::id).toList());
        assertEquals(2, result.summary().menswear());
        assertEquals(2, result.summary().womenswear());
        assertEquals(1, result.summary().niche());
    }

    @Test
    void choosesAnotherNicheWhenFirstNicheWouldBlockACompleteSet() {
        var secondMan = withImage(item("m2", "男装穿搭", List.of()), "https://images.example/shared.jpg");
        var firstNiche = withImage(item("n1", "哥特风穿搭", List.of()), "https://images.example/shared.jpg");
        var result = TrendDailySelection.select(List.of(item("m1", "男装穿搭", List.of()), secondMan,
                firstNiche, item("n2", "蒸汽朋克穿搭", List.of()),
                item("w1", "女装穿搭", List.of()), item("w2", "女装穿搭", List.of())));
        assertEquals(List.of("m1", "m2", "n2", "w1", "w2"), result.items().stream().map(TrendItem::id).toList());
        assertEquals(0, result.summary().missing().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void replacesHigherRankedManWhenHisImageWouldBlockTheSecondWomenswearSlot() {
        var firstMan = withImage(item("m1", "男装穿搭", List.of()), "https://images.example/shared.jpg");
        var secondWoman = withImage(item("w2", "女装穿搭", List.of()), "https://images.example/shared.jpg");
        var result = TrendDailySelection.select(List.of(firstMan, item("m2", "男装穿搭", List.of()),
                item("m3", "男装穿搭", List.of()), item("w1", "女装穿搭", List.of()),
                secondWoman, item("n1", "哥特风穿搭", List.of())));
        assertEquals(List.of("m2", "m3", "w1", "w2", "n1"), result.items().stream().map(TrendItem::id).toList());
        assertEquals(0, result.summary().missing().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void choosesEarliestRanksAmongCompleteSetsFrom150Candidates() {
        List<TrendItem> candidates = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            candidates.add(item("m" + index, "男装穿搭", List.of()));
            candidates.add(item("w" + index, "女装穿搭", List.of()));
            candidates.add(item("n" + index, "哥特风穿搭", List.of()));
        }
        var result = TrendDailySelection.select(candidates);
        assertEquals(List.of("m0", "w0", "n0", "m1", "w1"), result.items().stream().map(TrendItem::id).toList());
    }

    @Test
    void leavesMissingCategoriesEmptyInsteadOfFillingFromUnknownOrExtraMen() {
        var result = TrendDailySelection.select(List.of(item("m1", "男士穿搭", List.of()),
                item("m2", "男装搭配", List.of()), item("m3", "男生穿搭", List.of()),
                item("unknown", "秋季通勤穿搭", List.of()), item("generic", "小众穿搭", List.of())));
        assertEquals(2, result.items().size());
        assertEquals(2, result.summary().missing().get("womenswear"));
        assertEquals(1, result.summary().missing().get("niche"));
    }

    @Test
    void followerTierAndAuthorNameCannotSupplyAnyCategory() {
        var evidence = new TrendEvidence("男装博主", "image", List.of(), null, null, null, null, null, null,
                100L, "100", "niche");
        var unknown = withEvidence(item("unknown", "通勤穿搭", List.of()), evidence);
        var mainstream = withEvidence(item("mainstream", "女装穿搭", List.of()), evidence);
        var niche = withEvidence(item("niche", "洛丽塔穿搭", List.of()), new TrendEvidence("author", "image", List.of(),
                null, null, null, null, null, null, 5_000_000L, "500万", "mainstream"));
        var result = TrendDailySelection.select(List.of(unknown, mainstream, niche));
        assertEquals(List.of("mainstream", "niche"), result.items().stream().map(TrendItem::id).toList());
        assertEquals(1, result.summary().womenswear());
        assertEquals(1, result.summary().niche());
    }

    @Test
    void ambiguousOrNegatedApparelLabelsDoNotFillMainSlots() {
        var result = TrendDailySelection.select(List.of(
                item("both", "男装穿搭和女装穿搭对照", List.of()),
                item("unisex", "男士穿搭，男女同款", List.of()),
                item("negated", "不是男装穿搭", List.of()),
                item("tag-conflict", "男士穿搭", List.of("女装穿搭")),
                item("woman", "不是哥特风的女装穿搭", List.of()),
                item("niche-negated", "不是小众穿搭的男装穿搭", List.of())));
        assertEquals(List.of("woman", "niche-negated"), result.items().stream().map(TrendItem::id).toList());
        assertEquals(0, result.summary().niche());
        assertEquals(1, result.summary().womenswear());
        assertEquals(1, result.summary().menswear());
    }

    @Test
    void nicheRequiresNamedStyleAndApparelContext() {
        var result = TrendDailySelection.select(List.of(
                item("no-style", "小众穿搭", List.of("男装穿搭")),
                item("architecture", "哥特式建筑欣赏", List.of()),
                item("low-follower", "小众博主男装穿搭", List.of()),
                item("named", "作品分享", List.of("小众穿搭", "暗黑先锋"))));
        assertEquals(List.of("low-follower", "named"), result.items().stream().map(TrendItem::id).toList());
        assertEquals(1, result.summary().menswear());
        assertEquals(1, result.summary().niche());
    }

    @Test
    void architecturalOrGameBackgroundDoesNotDefineTheClothesStyle() {
        var result = TrendDailySelection.select(List.of(
                item("architecture", "男装穿搭在哥特建筑前", List.of()),
                item("background", "男装穿搭与赛博朋克背景", List.of()),
                item("movie", "洛丽塔电影观后感", List.of())));
        assertEquals(2, result.summary().menswear());
        assertEquals(0, result.summary().niche());
    }

    @Test
    void samePostIdOrTrackedSourceCannotOccupyTwoSlots() {
        var first = withSource(item("same", "男士穿搭", List.of()), "https://example.com/post?id=42&utm_source=a");
        var sameId = withSource(item("same", "女装穿搭", List.of()), "https://other.example/post/2");
        var sameSource = withSource(item("other", "女装穿搭", List.of()), "https://example.com/post?spm=123&id=42#share");
        var differentPost = withSource(item("different", "女装穿搭", List.of()), "https://example.com/post?id=43");
        var result = TrendDailySelection.select(List.of(first, sameId, sameSource, differentPost));
        assertEquals(List.of("same", "different"), result.items().stream().map(TrendItem::id).toList());
    }

    @Test
    void sharedImagesAcrossPlatformsAndSinaSizesAreDeduplicated() {
        var first = withImage(item("weibo", "男装穿搭", List.of()), "https://wx1.sinaimg.cn/middle/shared.jpg?token=one");
        var repost = withImage(item("xiaohongshu", "女装穿搭", List.of()), "https://wx4.sinaimg.cn/large/shared.jpg?token=two");
        var viaEvidence = withEvidence(item("douyin", "女装穿搭", List.of()),
                new TrendEvidence("someone", "image", List.of(),
                        null, null, null, null, null, null, null, null, null,
                        "https://wx2.sinaimg.cn/orj360/shared.jpg"));
        var different = withImage(item("different", "女装穿搭", List.of()), "https://wx2.sinaimg.cn/large/other.jpg");
        var result = TrendDailySelection.select(List.of(first, repost, viaEvidence, different));
        assertEquals(List.of("weibo", "different"), result.items().stream().map(TrendItem::id).toList());
        assertEquals(1, result.summary().missing().get("niche"));
    }

    @Test
    void sharedLifestylePhotoDoesNotMergeDifferentOutfits() {
        var commonCafePhoto = new TrendEvidence("someone", "image", List.of("https://images.example/coffee.jpg"),
                null, null, null, null, null, null);
        var result = TrendDailySelection.select(List.of(
                withEvidence(item("outfit-one", "男装穿搭", List.of()), commonCafePhoto),
                withEvidence(item("outfit-two", "女装穿搭", List.of()), commonCafePhoto)));
        assertEquals(2, result.items().size());
    }

    @Test
    void queryIdentityDistinguishesImagesWhileTrackingDoesNot() {
        var first = withImage(item("one", "男装穿搭", List.of()), "https://images.example/render?id=one&utm_source=a");
        var duplicate = withImage(item("duplicate", "男装穿搭", List.of()), "https://images.example/render?utm_source=b&id=one");
        var second = withImage(item("two", "男装穿搭", List.of()), "https://images.example/render?id=two");
        var result = TrendDailySelection.select(List.of(first, duplicate, second));
        assertEquals(List.of("one", "two"), result.items().stream().map(TrendItem::id).toList());
        assertEquals(2, result.summary().menswear());
    }

    @Test
    void sameAuthorNameDoesNotDeduplicateDistinctOutfits() {
        var evidence = new TrendEvidence("同名作者", "image", List.of(), null, null, null, null, null, null);
        var result = TrendDailySelection.select(List.of(withEvidence(item("one", "男装穿搭", List.of()), evidence),
                withEvidence(item("two", "男士搭配", List.of()), evidence)));
        assertEquals(2, result.items().size());
    }

    @Test
    void emptyInputHasExplicitMissingCounts() {
        var result = TrendDailySelection.select(null);
        assertTrue(result.items().isEmpty());
        assertEquals(5, result.summary().missing().values().stream().mapToInt(Integer::intValue).sum());
    }

    private static TrendItem item(String id, String title, List<String> tags) {
        return new TrendItem(id, "weibo", title, tags, 1, Instant.parse("2026-10-16T01:00:00Z"),
                Instant.parse("2026-10-16T01:00:00Z"), "https://example.com/" + id, false,
                "https://images.example/" + id + ".jpg");
    }

    private static TrendItem withEvidence(TrendItem item, TrendEvidence evidence) {
        return new TrendItem(item.id(), item.platform(), item.title(), item.topicTags(), item.heatScore(),
                item.publishedAt(), item.fetchedAt(), item.sourceUrl(), item.stale(), item.imageUrl(), item.summary(), evidence);
    }

    private static TrendItem withSource(TrendItem item, String source) {
        return new TrendItem(item.id(), item.platform(), item.title(), item.topicTags(), item.heatScore(),
                item.publishedAt(), item.fetchedAt(), source, item.stale(), item.imageUrl(), item.summary(), item.evidence());
    }

    private static TrendItem withImage(TrendItem item, String image) {
        return new TrendItem(item.id(), item.platform(), item.title(), item.topicTags(), item.heatScore(),
                item.publishedAt(), item.fetchedAt(), item.sourceUrl(), item.stale(), image, item.summary(), item.evidence());
    }
}
