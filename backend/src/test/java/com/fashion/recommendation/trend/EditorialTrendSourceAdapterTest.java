package com.fashion.recommendation.trend;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditorialTrendSourceAdapterTest {

    /** Later than every fixture timestamp, so only the explicit future check rejects an item. */
    private static final Instant NOW = Instant.parse("2026-09-18T09:00:00Z");

    @Test void keepsFashionItemsAndExcludesNonFashionDuplicatesAndMalformedEntries() {
        String item = """
                <item><title>Denim street style</title><link>https://www.vogue.com/article/test</link>
                <pubDate>Tue, 15 Sep 2026 05:35:45 +0000</pubDate><category>Fashion</category>
                <media:content/><media:thumbnail url="https://assets.vogue.com/test.jpg"/><dc:creator>Author</dc:creator>
                <description>Short original excerpt</description></item>
                """;
        String nonFashion = """
                <item><title>Baking sourdough at home</title><link>https://www.vogue.com/article/beauty</link>
                <pubDate>Tue, 15 Sep 2026 05:35:45 +0000</pubDate><category>Beauty</category></item>
                """;
        var items = EditorialTrendSourceAdapter.parse(
                "<rss><channel>" + item + item + nonFashion + "<item><title>broken</title></item></channel></rss>", NOW);
        assertEquals(1, items.size(), "the repeated link collapses and non-fashion/broken entries are dropped");
        assertEquals("https://assets.vogue.com/test.jpg", items.get(0).imageUrl());
        assertEquals("Author", items.get(0).evidence().author());
        assertTrue(items.get(0).topicTags().contains("丹宁"));
        assertNull(items.get(0).evidence().likes());
        assertEquals(0, items.get(0).heatScore());
    }

    @Test void parsesChinesePublisherFeedWithoutCategoryTags() {
        String xml = """
                <rss><channel><item>
                <title>秋冬通勤穿搭：针织与丹宁的层次叠穿</title>
                <link>https://hypebeast.cn/2026/9/autumn-layering</link>
                <pubDate>Thu, 17 Sep 2026 02:10:00 +0800</pubDate>
                <description><![CDATA[<p>用针织衫叠穿丹宁外套，通勤也能有层次。</p>]]></description>
                <enclosure url="https://image-cdn.hypebeast.cn/cover.jpg" type="image/jpeg"/>
                </item></channel></rss>
                """;
        var items = EditorialTrendSourceAdapter.parse(xml, NOW);
        assertEquals(1, items.size());
        TrendItem item = items.get(0);
        assertEquals("editorial", item.platform());
        assertEquals("https://image-cdn.hypebeast.cn/cover.jpg", item.imageUrl());
        assertTrue(item.topicTags().containsAll(List.of("通勤", "丹宁", "针织", "层次叠穿")));
        assertEquals(Instant.parse("2026-09-16T18:10:00Z"), item.publishedAt(), "+0800 must be normalised to UTC");
        assertTrue(item.summary().contains("针织衫叠穿丹宁外套"));
        assertFalse(item.summary().contains("<p>"), "summary must be plain text");
        assertEquals(0, item.heatScore(), "editorial sources have no platform heat");
    }

    @Test void dropsNonFashionUndatedFutureAndNonWebLinkItems() {
        String xml = """
                <rss><channel>
                <item><title>iPhone 17 评测：相机大升级</title><link>https://hypebeast.cn/2026/9/phone</link>
                <pubDate>Thu, 17 Sep 2026 02:10:00 +0800</pubDate></item>
                <item><title>极简通勤穿搭</title><link>https://hypebeast.cn/2026/9/missing-date</link></item>
                <item><title>极简通勤穿搭</title><link>javascript:alert(1)</link>
                <pubDate>Thu, 17 Sep 2026 02:10:00 +0800</pubDate></item>
                <item><title>极简通勤穿搭</title><link>/relative/path</link>
                <pubDate>Thu, 17 Sep 2026 02:10:00 +0800</pubDate></item>
                <item><title>极简通勤穿搭</title><link>https://user:pw@hypebeast.cn/2026/9/creds</link>
                <pubDate>Thu, 17 Sep 2026 02:10:00 +0800</pubDate></item>
                <item><title>极简通勤穿搭</title><link>https://hypebeast.cn/2026/9/future</link>
                <pubDate>Fri, 25 Dec 2026 02:10:00 +0800</pubDate></item>
                </channel></rss>
                """;
        assertEquals(List.of(), EditorialTrendSourceAdapter.parse(xml, NOW));
    }

    @Test void readsTheInlineImageChineseTrendFeedsActuallyPublish() {
        String xml = """
                <rss><channel><item>
                <title>Kiko Kostadinov 与 ASICS 推出分趾运动鞋</title>
                <link>https://hypebeast.cn/2026/9/kiko-kostadinov-asics</link>
                <pubDate>Fri, 18 Sep 2026 02:07:38 +0000</pubDate>
                <description><![CDATA[<p><img width="620" src="https://image-cdn.hypb.st/files/2026/09/kiko.jpg?w=800"/></p><p>联名系列收官。</p>]]></description>
                </item></channel></rss>
                """;
        var items = EditorialTrendSourceAdapter.parse(xml, NOW);
        assertEquals(1, items.size(), "these feeds publish no media:content or enclosure tag");
        assertEquals("https://image-cdn.hypb.st/files/2026/09/kiko.jpg?w=800", items.get(0).imageUrl());
        assertTrue(items.get(0).summary().contains("联名系列收官"));
        assertFalse(items.get(0).summary().contains("<p>"), "summary must be plain text");
    }

    @Test void fallsBackToContentEncodedWhenDescriptionIsEmpty() {
        String xml = """
                <rss><channel><item>
                <title>复古针织开衫的叠穿思路</title>
                <link>https://hypebeast.cn/2026/9/knit</link>
                <pubDate>Fri, 18 Sep 2026 02:07:38 +0000</pubDate>
                <description></description>
                <content:encoded><![CDATA[<p><img src="https://image-cdn.hypb.st/knit.jpg"/></p><p>复古针织开衫叠穿示范。</p>]]></content:encoded>
                </item></channel></rss>
                """;
        var items = EditorialTrendSourceAdapter.parse(xml, NOW);
        assertEquals(1, items.size());
        assertEquals("https://image-cdn.hypb.st/knit.jpg", items.get(0).imageUrl());
        assertTrue(items.get(0).summary().contains("复古针织开衫"));
    }

    @Test void acceptsHttpPublisherLinksButNeverEmbedsAnHttpImage() {
        String xml = """
                <rss><channel><item>
                <title>专访：轻户外机能外套的搭配思路</title>
                <link>http://www.toodaylab.com/84209</link>
                <pubDate>Thu, 17 Sep 2026 14:03:04 +0000</pubDate>
                <description>&lt;p&gt;&lt;img src="http://cdn.toodaylab.com/a.jpg" /&gt;轻户外机能外套怎么搭&lt;/p&gt;</description>
                </item></channel></rss>
                """;
        var items = EditorialTrendSourceAdapter.parse(xml, NOW);
        assertEquals(1, items.size(), "a plain-HTTP permalink must not drop the article");
        assertEquals("http://www.toodaylab.com/84209", items.get(0).sourceUrl());
        assertEquals("轻户外", items.get(0).topicTags().get(0));
        assertNull(items.get(0).imageUrl(), "an http image cannot load inside an https page");
    }

    @Test void ignoresNonImageEnclosuresAndFallsBackToInlineImage() {
        String xml = """
                <rss><channel><item>
                <title>丹宁外套搭配</title><link>https://www.toodaylab.com/12345</link>
                <pubDate>Thu, 17 Sep 2026 02:10:00 +0800</pubDate>
                <enclosure url="https://cdn.toodaylab.com/clip.mp4" type="video/mp4"/>
                <description>&lt;img src="https://cdn.toodaylab.com/inline.jpg" /&gt;丹宁外套怎么搭</description>
                </item></channel></rss>
                """;
        var items = EditorialTrendSourceAdapter.parse(xml, NOW);
        assertEquals(1, items.size());
        assertEquals("https://cdn.toodaylab.com/inline.jpg", items.get(0).imageUrl());
    }

    @Test void feedListIsHttpsOnlyDeduplicatedAndFallsBackToTheBuiltInFeed() {
        assertEquals(List.of(EditorialTrendSourceAdapter.DEFAULT_FEED),
                EditorialTrendSourceAdapter.parseFeeds("   "));
        assertEquals(List.of(EditorialTrendSourceAdapter.DEFAULT_FEED),
                EditorialTrendSourceAdapter.parseFeeds("http://hypebeast.cn/feed"));
        assertEquals(List.of("https://hypebeast.cn/feed", "https://www.toodaylab.com/feed"),
                EditorialTrendSourceAdapter.parseFeeds(
                        "https://hypebeast.cn/feed,\n https://www.toodaylab.com/feed\nhttps://hypebeast.cn/feed"));
    }

    @Test void disabledSourceNeverFetchesAnything() {
        var adapter = new EditorialTrendSourceAdapter(false, "https://hypebeast.cn/feed");
        assertEquals("editorial", adapter.platform());
        assertEquals(List.of(), adapter.fetchPublicSnapshots());
    }
}
