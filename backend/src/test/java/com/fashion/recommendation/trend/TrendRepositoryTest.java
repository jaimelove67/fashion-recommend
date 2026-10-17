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
    @Test void neutralOotdTagsCannotPublishAnImageWithoutContentReview() {
        Instant now = Instant.now();
        var post = new TrendItem("weibo:body-closeup-regression", "weibo",
                "珍珠贵妇 #ootd# #今天穿什么# #时尚穿搭#", List.of("穿搭灵感"), 0,
                now.minusSeconds(60), now, "https://weibo.com/7128147746/RmFI2l1ka", false,
                "https://images.example/closeup.jpg", "普通穿搭标签不等于图片内容合格",
                new TrendEvidence("author", "image", List.of("https://images.example/closeup.jpg"),
                        null, null, null, null, "来源内评分", null));
        repository.save("weibo", post);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trend_contents WHERE id=?", Integer.class, post.id()));
        assertTrue(repository.find(post.id()).isEmpty(), "未复核图片不能作为搭配参考使用");
        assertFalse(repository.since(now.minusSeconds(3600)).stream().anyMatch(i -> i.id().equals(post.id())),
                "有 OOTD 标签和图片也不能跳过内容复核直接发布");
    }

    @Test void rejectedOrHiddenPostsCannotReappearAfterAnotherCollection() {
        Instant now = Instant.now();
        var post = item(now, null);
        repository.save("weibo", post);
        jdbc.update("UPDATE trend_contents SET moderation_status='REJECTED',hidden=TRUE WHERE id=?", post.id());
        repository.save("weibo", item(now.plusSeconds(1), 10L));
        assertTrue(repository.find(post.id()).isEmpty());
        assertFalse(repository.since(now.minusSeconds(3600)).stream().anyMatch(i -> i.id().equals(post.id())));
    }
    @Test void publishesXiaohongshuPostsAndRetainsHotBoardFiltering() {
        Instant now = Instant.now();
        TrendItem post = new TrendItem("xiaohongshu:repository-post", "xiaohongshu", "日常连衣裙穿搭",
                List.of("日常"), 0, now.minusSeconds(60), now, "https://www.xiaohongshu.com/explore/test",
                false, null, "真实图文", new TrendEvidence("author", "image",
                List.of("https://images.example/look.jpg"), null, null, null, null, "互动", null));
        TrendItem board = new TrendItem("weibo:repository-board", "weibo", "穿搭热榜", List.of(), 0,
                now, now, "https://weibo.com/test", false, null, "热榜",
                new TrendEvidence(null, "board", List.of(), null, null, null, null, "榜单", null));
        repository.save("xiaohongshu", post);
        repository.save("weibo", board);
        approve(post);
        jdbc.update("UPDATE trend_contents SET moderation_status='APPROVED',reviewed_content_hash=? WHERE id=?",
                repository.reviewCandidate(board.id()).orElseThrow().fingerprint(), board.id());

        assertEquals(post, repository.find(post.id()).orElseThrow());
        var publicIds = repository.since(now.minusSeconds(3600)).stream().map(TrendItem::id).toList();
        assertTrue(publicIds.contains(post.id()), "已入库的小红书图文必须进入公共列表");
        assertFalse(publicIds.contains(board.id()), "恢复平台支持不能放开热榜过滤");
        var includingBoards = repository.sinceIncludingHotBoards(now.minusSeconds(3600)).stream()
                .map(TrendItem::id).toList();
        assertTrue(includingBoards.containsAll(List.of(post.id(), board.id())));
    }

    @Test void persistsHistoryWithoutFabricatingGrowthAndHonorsContentReview() {
        Instant now = Instant.now();
        TrendItem first = item(now.minus(2, ChronoUnit.DAYS), 10L);
        repository.save("weibo", first);
        repository.save("weibo", first);
        assertNull(repository.growth(first, now.minus(3, ChronoUnit.DAYS)));
        TrendItem current = item(now, 40L);
        repository.save("weibo", current);
        approve(current);
        assertEquals(30L, repository.growth(current, now.minus(1, ChronoUnit.DAYS)));
        assertNull(repository.growth(item(now, 5L), now.minus(1, ChronoUnit.DAYS)));
        assertEquals(40L, repository.find(current.id()).orElseThrow().evidence().likes());
        assertTrue(repository.since(now.minus(1, ChronoUnit.DAYS)).stream()
                .anyMatch(item -> item.id().equals(current.id())), "复核通过的内容可进入公共查询");
        jdbc.update("UPDATE trend_contents SET moderation_status='REJECTED', hidden=TRUE WHERE id=?", current.id());
        assertTrue(repository.find(current.id()).isEmpty());
        assertFalse(repository.since(now.minus(1, ChronoUnit.DAYS)).stream()
                .anyMatch(item -> item.id().equals(current.id())), "被拒绝或隐藏的内容不能继续公开");
    }

    @Test void counterRefreshKeepsApprovalButChangedImagesRequireAnotherReview() {
        Instant now = Instant.now();
        var first = item(now, 10L);
        repository.save("weibo", first);
        String originalHash = repository.reviewCandidate(first.id()).orElseThrow().fingerprint();
        approve(first);
        repository.save("weibo", item(now.plusSeconds(1), 20L));
        assertTrue(repository.find(first.id()).isPresent(), "互动数更新不应重复复核");
        var changed = new TrendItem(first.id(), first.platform(), first.title(), first.topicTags(), 0,
                first.publishedAt(), now.plusSeconds(2), first.sourceUrl(), false, "https://images.example/changed.jpg",
                first.summary(), new TrendEvidence("author", "image", List.of("https://images.example/changed.jpg"),
                null, null, null, null, "互动", null));
        repository.save("weibo", changed);
        assertTrue(repository.find(first.id()).isEmpty());
        assertFalse(repository.review(first.id(), originalHash, true, "test-reviewer", "过期版本"));
        assertEquals("PENDING_HUMAN", repository.reviewCandidate(first.id()).orElseThrow().status());
        approve(changed);
        assertTrue(repository.find(first.id()).isPresent());
        jdbc.update("UPDATE trend_contents SET hidden=TRUE WHERE id=?", first.id());
        assertTrue(repository.find(first.id()).isEmpty(), "隐藏状态优先于通过复核");
    }

    private void approve(TrendItem item) {
        assertTrue(repository.review(item.id(), repository.reviewCandidate(item.id()).orElseThrow().fingerprint(),
                true, "test-reviewer", "服装展示清晰，未见身体特写或性暗示"));
    }

    @Test void changingOutfitCategoryInvalidatesTheReviewedVersion() {
        var first = item(Instant.now(), 10L);
        repository.save("weibo", first);
        approve(first);
        String approvedHash = repository.reviewCandidate(first.id()).orElseThrow().fingerprint();
        var changed = new TrendItem(first.id(), first.platform(), first.title(),
                List.of("小众穿搭", "哥特风"), first.heatScore(), first.publishedAt(), first.fetchedAt(),
                first.sourceUrl(), false, first.imageUrl(), first.summary(), first.evidence());
        repository.save("weibo", changed);
        assertTrue(repository.find(first.id()).isEmpty());
        assertEquals("PENDING_HUMAN", repository.reviewCandidate(first.id()).orElseThrow().status());
        assertFalse(repository.review(first.id(), approvedHash, true, "reviewer", "旧分类版本不能继续批准"));
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
                at.minus(1, ChronoUnit.DAYS), at, "https://weibo.com/test", false, "https://images.example/outfit.jpg", "真实参考",
                new TrendEvidence("author", "image", List.of("https://images.example/outfit.jpg"), likes, null, null, null, "互动", null));
    }
}
