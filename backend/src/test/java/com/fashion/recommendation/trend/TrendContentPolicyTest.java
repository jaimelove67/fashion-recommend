package com.fashion.recommendation.trend;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrendContentPolicyTest {
    @Test void rejectsBodyFocusedAndSuggestiveCopyButKeepsOrdinaryClothingDetails() {
        for (String text : List.of("OOTD 胸部特写", "穿搭 擦边福利", "时尚 性暗示", "OnlyFans 穿搭")) {
            assertNotNull(TrendContentPolicy.exclusionReason(item(text)), text);
        }
        for (String text : List.of("正常半身针织衫穿搭", "衬衫胸前口袋细节", "珍珠项链搭配细节",
                "吊带配开衫的通勤穿搭", "大胸女生衬衫尺码与版型建议")) {
            assertNull(TrendContentPolicy.exclusionReason(item(text)), text);
            assertTrue(TrendContentPolicy.hasReviewableImage(item(text)));
        }
    }

    private TrendItem item(String title) {
        Instant now = Instant.now();
        return new TrendItem("weibo:policy-test", "weibo", title, List.of("穿搭"), 0, now, now,
                "https://weibo.com/test", false, "https://images.example/item.jpg", null,
                new TrendEvidence(null, "image", List.of("https://images.example/item.jpg"), null, null, null, null, "互动", null));
    }
}
