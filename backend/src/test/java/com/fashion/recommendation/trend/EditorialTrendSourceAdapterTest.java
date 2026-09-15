package com.fashion.recommendation.trend;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditorialTrendSourceAdapterTest {
    @Test void keepsFashionImagesButExcludesBeautyAndMalformedItems() {
        String item = """
                <item><title>Denim street style</title><link>https://www.vogue.com/article/test</link>
                <pubDate>Tue, 15 Sep 2026 05:35:45 +0000</pubDate><category>Fashion</category>
                <media:content/><media:thumbnail url="https://assets.vogue.com/test.jpg"/><dc:creator>Author</dc:creator>
                <description>Short original excerpt</description></item>
                """;
        var items = EditorialTrendSourceAdapter.parse("<rss><channel>" + item + item.replace("Fashion", "Beauty")
                + "<item><title>broken</title></item></channel></rss>", Instant.now());
        assertEquals(1, items.size());
        assertEquals("https://assets.vogue.com/test.jpg", items.get(0).imageUrl());
        assertTrue(items.get(0).topicTags().contains("丹宁"));
        assertNull(items.get(0).evidence().likes());
        assertEquals(0, items.get(0).heatScore());
    }
}
