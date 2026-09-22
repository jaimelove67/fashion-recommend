package com.fashion.recommendation.trend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendTopicsTest {
    @Test
    void excludesRedCarpetRunwayAndCelebrityEditorialLooks() {
        for (String text : new String[] {
                "艾美奖红毯礼服造型",
                "2026 春夏时装周秀场趋势",
                "明星时装大片造型",
                "女星封面珠宝搭配"
        }) {
            assertTrue(TrendTopics.excludedShowOrCelebrity(text), text);
        }
    }

    @Test
    void keepsCreatorOutfitSharingAndPracticalStylingIdeas() {
        for (String text : new String[] {
                "秋冬通勤穿搭分享：一件外套的三种叠穿",
                "博主分享低饱和配色和日常搭配思路",
                "OOTD：牛仔裤与针织衫怎么穿"
        }) {
            assertFalse(TrendTopics.excludedShowOrCelebrity(text), text);
        }
    }

    @Test
    void boardFashionAlsoRejectsShowAndCelebrityTopics() {
        assertFalse(TrendTopics.boardFashion("红毯造型"));
        assertFalse(TrendTopics.boardFashion("时装周"));
        assertTrue(TrendTopics.boardFashion("秋冬外套穿搭分享"));
    }
}
