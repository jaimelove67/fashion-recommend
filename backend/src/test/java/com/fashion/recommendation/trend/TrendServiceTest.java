package com.fashion.recommendation.trend;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendServiceTest {
    private static final Instant FETCHED_AT = Instant.parse("2026-07-21T09:00:00Z");

    @Test
    void fallsBackToExplicitDevelopmentSamplesWhenTheSourceFails() {
        TrendSourceAdapter failingSource = source("licensed-feed", () -> {
            throw new TrendSourceException("upstream unavailable");
        });

        TrendFeed result = new TrendService(List.of(failingSource)).currentFeed(null, null);

        assertTrue(result.demoMode());
        assertEquals("douyin-development-sample", result.primarySource());
        assertEquals(3, result.items().size());
    }

    @Test
    void keepsARealFeedWhenFilteringProducesNoMatches() {
        TrendItem item = new TrendItem(
                "urban-layering",
                "licensed-feed",
                "轻机能通勤的层次感",
                List.of("通勤", "轻机能"),
                92,
                Instant.parse("2026-07-21T08:00:00Z"),
                FETCHED_AT,
                "https://source.example/trends/urban-layering",
                false,
                "https://source.example/images/urban-layering.jpg");
        TrendSourceAdapter validSource = source("licensed-feed", () -> List.of(item));

        TrendFeed result = new TrendService(List.of(validSource))
                .currentFeed("licensed-feed", "不存在的主题");

        assertFalse(result.demoMode());
        assertEquals("licensed-feed", result.primarySource());
        assertEquals(FETCHED_AT, result.fetchedAt());
        assertTrue(result.items().isEmpty());
    }

    private static TrendSourceAdapter source(String platform, ItemSupplier supplier) {
        return new TrendSourceAdapter() {
            @Override
            public String platform() {
                return platform;
            }

            @Override
            public List<TrendItem> fetchPublicSnapshots() {
                return supplier.get();
            }
        };
    }

    @FunctionalInterface
    private interface ItemSupplier {
        List<TrendItem> get();
    }
}
