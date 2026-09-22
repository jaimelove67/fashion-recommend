package com.fashion.recommendation.trend;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TrendRepositoryTest {
    @Autowired TrendRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Test void persistsHistoryWithoutFabricatingGrowthAndPublishesWithoutModeration() {
        Instant now = Instant.now();
        TrendItem first = item(now.minus(2, ChronoUnit.DAYS), 10L);
        repository.save("weibo", first);
        repository.save("weibo", first);
        assertNull(repository.growth(first, now.minus(3, ChronoUnit.DAYS)));
        TrendItem current = item(now, 40L);
        repository.save("weibo", current);
        assertEquals(30L, repository.growth(current, now.minus(1, ChronoUnit.DAYS)));
        assertNull(repository.growth(item(now, 5L), now.minus(1, ChronoUnit.DAYS)));
        assertEquals(40L, repository.find(current.id()).orElseThrow().evidence().likes());
        assertTrue(repository.since(now.minus(1, ChronoUnit.DAYS)).stream()
                .anyMatch(item -> item.id().equals(current.id())), "新抓取内容应立即进入公共查询");
        jdbc.update("UPDATE trend_contents SET moderation_status='REJECTED', hidden=TRUE WHERE id=?", current.id());
        assertTrue(repository.find(current.id()).isPresent());
        assertTrue(repository.since(now.minus(1, ChronoUnit.DAYS)).stream()
                .anyMatch(item -> item.id().equals(current.id())), "已弃用的审核字段不能再阻断旧行查询");
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
