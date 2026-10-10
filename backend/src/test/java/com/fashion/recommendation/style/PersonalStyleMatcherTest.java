package com.fashion.recommendation.style;

import com.fashion.recommendation.wardrobe.WardrobeItem;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalStyleMatcherTest {
    @Test
    void confirmedLikesOutrankPhotoSuggestionsAndConfirmedAvoidancesReduceRanking() {
        StyleProfile profile = new StyleProfile("用户", null, List.of("运动"), List.of("黑色"), List.of(),
                List.of("极简"), List.of(), List.of("米白"), List.of(), "参考", "test", Instant.now(), false)
                .withPreferenceDetails(List.of("紧身"), true);
        WardrobeItem preferred = item(1, "宽松运动上衣", "黑色", "运动");
        WardrobeItem suggested = item(2, "基础上衣", "米白", "极简");
        WardrobeItem avoided = item(3, "紧身运动上衣", "黑色", "运动");
        assertTrue(PersonalStyleMatcher.score(preferred, profile) > PersonalStyleMatcher.score(suggested, profile));
        assertTrue(PersonalStyleMatcher.score(preferred, profile) > PersonalStyleMatcher.score(avoided, profile));
    }

    private WardrobeItem item(long id, String name, String color, String style) {
        return new WardrobeItem(id, name, "上装", color, style, null, Instant.now());
    }
}
