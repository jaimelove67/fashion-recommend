package com.fashion.recommendation.trend;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicHotBoardsTest {

    private static final Instant OBSERVED = Instant.parse("2026-09-18T02:51:24Z");

    @Test void parsesDouyinBoardButKeepsOnlyFashionWords() {
        String json = """
                {"active_time":"2026-09-18 10:51:24","status_code":0,"word_list":[
                {"word":"九一八事变爆发95周年","hot_value":12141272,"label":0},
                {"word":"秋冬外套一衣穿三季穿搭分享","hot_value":9584102,"label":0},
                {"word":"到四川就要耍起","hot_value":9100000,"label":0},
                {"word":"少女感是早秋穿搭天花板","hot_value":7401924,"label":0}]}
                """;
        var items = PublicHotBoards.parseDouyin(json, OBSERVED);
        assertEquals(2, items.size());
        assertEquals("douyin", items.get(0).platform());
        assertEquals("秋冬外套一衣穿三季穿搭分享", items.get(0).title());
        assertTrue(items.get(0).sourceUrl().startsWith("https://www.douyin.com/search/"));
        assertTrue(items.get(0).sourceUrl().endsWith("%E7%A9%BF%E6%90%AD%E5%88%86%E4%BA%AB"));
        assertEquals(OBSERVED, items.get(0).publishedAt(), "a board word has no publish time, the observation is used");
        assertEquals(OBSERVED, items.get(0).fetchedAt());
        assertNull(items.get(0).imageUrl());
        assertTrue(items.get(0).summary().contains("第 2 位"));
        assertTrue(items.get(0).summary().contains("9,584,102"), "the raw platform heat value stays visible");
        assertEquals("平台热榜热度（归一化）", items.get(0).evidence().scoreLabel());
        assertFalse(items.get(0).evidence().hasCounters(), "board heat is not engagement");
    }

    @Test void normalisationUsesTheWholeBoardSoTheTopWordScores100() {
        String json = """
                {"word_list":[
                {"word":"九一八事变爆发95周年","hot_value":12141272},
                {"word":"少女感是早秋穿搭天花板","hot_value":7401924},
                {"word":"丹宁外套叠穿分享","hot_value":12141272}]}
                """;
        var items = PublicHotBoards.parseDouyin(json, OBSERVED);
        assertEquals(100, items.stream().mapToInt(TrendItem::heatScore).max().orElse(0),
                "a word tying the board maximum scores 100");
        assertTrue(items.stream().allMatch(i -> i.heatScore() >= 0 && i.heatScore() <= 100));
        var lower = items.stream().filter(i -> i.title().contains("少女感")).findFirst().orElseThrow();
        assertTrue(lower.heatScore() < 100, "a lower heat word must score below the board maximum");
    }

    @Test void parsesWeiboBoardWithHeatValues() {
        String json = """
                {"ok":1,"data":{"realtime":[
                {"flag":16,"note":"918","num":9180000,"icon_desc":"沸"},
                {"note":"我们来了 刘雯","num":2000000},
                {"note":"极简通勤穿搭示范","num":1500000}],
                "hotgov":{"word":"要闻"}}}
                """;
        var items = PublicHotBoards.parseWeibo(json, OBSERVED);
        assertEquals(List.of("极简通勤穿搭示范"), items.stream().map(TrendItem::title).toList());
        assertEquals("weibo", items.get(0).platform());
        assertTrue(items.get(0).sourceUrl().startsWith("https://s.weibo.com/weibo?q="));
        assertEquals("平台热榜热度（归一化）", items.get(0).evidence().scoreLabel());
        assertTrue(items.get(0).heatScore() < 100, "not the board maximum");
        assertTrue(items.get(0).heatScore() > 0);
        assertEquals("微博热搜", items.get(0).evidence().author());
    }

    @Test void weiboBoardWithoutHeatNumbersFallsBackToRankScores() {
        String json = """
                {"ok":1,"data":{"realtime":[
                {"note":"九一八事变"},
                {"note":"丹宁穿搭天花板"},
                {"note":"机顶盒退场"},
                {"note":"针织开衫搭配"}]}}
                """;
        var items = PublicHotBoards.parseWeibo(json, OBSERVED);
        assertEquals(2, items.size());
        assertEquals("平台热榜位次", items.get(0).evidence().scoreLabel());
        assertTrue(items.get(0).heatScore() > items.get(1).heatScore(),
                "a higher board position must score higher");
        assertFalse(items.stream().anyMatch(i -> i.summary().contains("热度值")));
    }

    @Test void duplicateFashionWordsCollapseToOneItem() {
        String json = """
                {"word_list":[
                {"word":"通勤穿搭","hot_value":100},
                {"word":"通勤穿搭","hot_value":100}]}
                """;
        assertEquals(1, PublicHotBoards.parseDouyin(json, OBSERVED).size());
    }

    @Test void invalidBoardResponsesAreRejectedNotEmptied() {
        assertThrows(TrendSourceException.class, () -> PublicHotBoards.parseDouyin("{\"word_list\":[]}", OBSERVED));
        assertThrows(TrendSourceException.class, () -> PublicHotBoards.parseDouyin("{\"other\":1}", OBSERVED));
        assertThrows(TrendSourceException.class, () -> PublicHotBoards.parseWeibo("{\"ok\":0}", OBSERVED));
        assertThrows(TrendSourceException.class, () -> PublicHotBoards.parseWeibo("not json", OBSERVED));
    }

    @Test void onlyDouyinAndWeiboHaveAPublicBoard() {
        assertTrue(PublicHotBoards.supports("douyin"));
        assertTrue(PublicHotBoards.supports("weibo"));
        assertFalse(PublicHotBoards.supports("xiaohongshu"), "xiaohongshu has no anonymous board");
    }

    @Test void shortBoardWordsNeedAnExplicitFashionMarker() {
        // Every rejected word below is real board wording, and each one would pass the recall-oriented
        // article classifier: 机能 hides inside 手机能换, 街头 inside 街头采访, 裙 inside 裙带关系,
        // 复古 inside 复古游戏, 运动 inside 五四运动. The live run on 2026-09-18 served the phone-price
        // topic as 轻户外 fashion, which is what this stricter rule exists to prevent.
        for (String notFashion : List.of(
                "才意识到一台苹果手机能换一万斤粮食",
                "街头随机采访路人",
                "裙带关系有多可怕",
                "复古游戏重制版重登热销榜",
                "五四运动纪念日")) {
            assertFalse(TrendTopics.boardFashion(notFashion), notFashion + " must not count as fashion");
        }
        for (String fashion : List.of(
                "秋冬外套一衣穿三季穿搭分享",
                "少女感是早秋穿搭天花板",
                "丹宁外套叠穿分享",
                "针织开衫搭配",
                "极简通勤穿搭示范")) {
            assertTrue(TrendTopics.boardFashion(fashion), fashion + " must count as fashion");
        }
    }

    @Test void boardWordsAreFilteredByTheStrictRuleNotTheArticleRule() {
        String json = """
                {"word_list":[
                {"word":"才意识到一台苹果手机能换一万斤粮食","hot_value":163779},
                {"word":"秋冬外套一衣穿三季穿搭分享","hot_value":10261945}]}
                """;
        var items = PublicHotBoards.parseDouyin(json, OBSERVED);
        assertEquals(1, items.size());
        assertEquals("秋冬外套一衣穿三季穿搭分享", items.get(0).title());
    }
}
