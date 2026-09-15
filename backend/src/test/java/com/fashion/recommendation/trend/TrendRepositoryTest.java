package com.fashion.recommendation.trend;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TrendRepositoryTest {
    @Autowired TrendRepository repository;
    @Test void persistsHistoryWithoutFabricatingGrowthAndRetainsModeration() {
        Instant now = Instant.now();
        TrendItem first = item(now.minus(2, ChronoUnit.DAYS), 10L);
        repository.save("weibo", first);
        repository.save("weibo", first);
        assertNull(repository.growth(first, now.minus(3, ChronoUnit.DAYS)));
        TrendItem current = item(now, 40L);
        repository.save("weibo", current);
        assertEquals(30L, repository.growth(current, now.minus(1, ChronoUnit.DAYS)));
        assertNull(repository.growth(item(now, 5L), now.minus(1, ChronoUnit.DAYS)));
        assertTrue(repository.find(current.id()).isEmpty(), "未完成人工终审的趋势不能进入公共查询");
        assertEquals(1, repository.pendingAi(10).stream().filter(i -> i.id().equals(current.id())).count());
        assertTrue(repository.markAiReviewed(
                current.id(),
                new TrendAiReviewResult("PASS", "LOW", "与穿搭内容相关", "qwen-plus", "call-1", "trend-moderation-v1"),
                now));
        assertTrue(repository.finalizeHuman(current.id(), "APPROVED", "admin", now, "人工确认通过"));
        assertEquals(40L, repository.find(current.id()).orElseThrow().evidence().likes());
        repository.hide(current.id(), true);
        repository.save("weibo", current);
        assertTrue(repository.find(current.id()).isEmpty());
        repository.hide(current.id(), false);
        assertTrue(repository.find(current.id()).isPresent());
    }
    @Test void aFailedRefreshPreservesLastSuccessfulTime() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        repository.status("test-source", now, true, 3, "ok");
        repository.status("test-source", now.plusSeconds(10), false, 0, "unavailable");
        var status = repository.statuses().stream().filter(s -> s.id().equals("test-source")).findFirst().orElseThrow();
        assertEquals(now, status.lastSuccessAt());
        assertEquals("unavailable", status.state());
    }
    private TrendItem item(Instant at, Long likes) {
        return new TrendItem("weibo:repository-test", "weibo", "通勤", List.of("通勤"), 0,
                at.minus(1, ChronoUnit.DAYS), at, "https://weibo.com/test", false, null, "真实参考",
                new TrendEvidence("author", "image", List.of(), likes, null, null, null, "互动", null));
    }
}
