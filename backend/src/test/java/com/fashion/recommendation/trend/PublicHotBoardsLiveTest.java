package com.fashion.recommendation.trend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Optional live check against the real public hot boards. Never runs in a normal offline
 * `mvn test`; enable explicitly with {@code -DliveTrendBoards=true} when the endpoints should be
 * re-verified, for example before a defense demo. An empty result is valid: a board that was read
 * successfully but holds no fashion word today must not fail this check.
 *
 * <p>Because Maven Surefire captures test stdout, the findings are also written to
 * {@code target/trend-live-report.txt} so the run leaves inspectable evidence.
 */
@EnabledIfSystemProperty(named = "liveTrendBoards", matches = "true")
class PublicHotBoardsLiveTest {
    @Test void readsTheRealPublicBoards() throws Exception {
        List<String> report = new ArrayList<>();
        report.add("公开热榜实测 " + Instant.now());
        for (String platform : new String[] {"douyin", "weibo"}) {
            var items = PublicHotBoards.fetch(platform, Instant.now());
            assertTrue(items.stream().allMatch(i -> i.platform().equals(platform)));
            assertTrue(items.stream().allMatch(i -> i.heatScore() >= 0 && i.heatScore() <= 100));
            assertTrue(items.stream().allMatch(i -> i.sourceUrl().startsWith("https://")));
            report.add(platform + ": " + items.size() + " 条穿搭相关热榜词");
            items.forEach(i -> report.add("  [" + i.heatScore() + "] " + i.title() + " · " + i.summary()
                    + " · " + i.topicTags() + " · " + i.sourceUrl()
                    + " · 评分口径=" + i.evidence().scoreLabel()));
        }
        Path target = Path.of("target", "trend-live-report.txt");
        Files.createDirectories(target.getParent());
        Files.write(target, report);
        report.forEach(System.out::println);
    }
}
