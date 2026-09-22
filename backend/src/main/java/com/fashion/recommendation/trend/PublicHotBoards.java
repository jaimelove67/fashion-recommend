package com.fashion.recommendation.trend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;

/**
 * Publicly readable platform hot boards, used as the built-in fallback of a social trend source
 * when no import file and no configured endpoint exists.
 *
 * <p>What these boards give is a ranked list of trending words with the platform's own heat value.
 * They do not give article bodies, images or per-word publication times, so every entry is built
 * from the word itself, the board position, the raw heat value and the observation time, and the
 * score label states plainly that the number is a normalised board heat, never engagement.
 * A board word is admitted only when it carries an explicit fashion marker
 * ({@link TrendTopics#boardFashion(String)}), because the recall-oriented article classifier
 * over-matches on short topic labels. Keyword search and note content are NOT collected here: those
 * endpoints require a login and are out of scope for this project.
 */
final class PublicHotBoards {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String USER_AGENT = "FashionResearch/1.0";
    private static final String DOUYIN_BOARD = "https://www.iesdouyin.com/web/api/v2/hotsearch/billboard/word/";
    private static final String WEIBO_BOARD = "https://weibo.com/ajax/side/hotSearch";
    private static final int MAX_BODY_BYTES = 2_000_000;
    private static final int TIMEOUT_MS = 12_000;

    private PublicHotBoards() {}

    /** Only platforms with an anonymously readable public board are supported. */
    static boolean supports(String platform) {
        return "douyin".equals(platform) || "weibo".equals(platform);
    }

    static List<TrendItem> fetch(String platform, Instant observed) {
        String url = "douyin".equals(platform) ? DOUYIN_BOARD : WEIBO_BOARD;
        try {
            var connection = Jsoup.connect(url).userAgent(USER_AGENT).timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES).ignoreContentType(true);
            if ("weibo".equals(platform)) connection.referrer("https://weibo.com/");
            String body = connection.execute().body();
            return "douyin".equals(platform) ? parseDouyin(body, observed) : parseWeibo(body, observed);
        } catch (Exception e) {
            throw new TrendSourceException("公开热榜暂不可用");
        }
    }

    /** Douyin publishes word_list entries as {"word": ..., "hot_value": ...}. */
    static List<TrendItem> parseDouyin(String json, Instant observed) {
        try {
            JsonNode words = MAPPER.readTree(json).path("word_list");
            if (!words.isArray() || words.isEmpty()) throw new TrendSourceException("抖音热榜响应无效");
            List<BoardEntry> board = new ArrayList<>();
            for (JsonNode node : words) {
                String word = node.path("word").asText("").trim();
                if (word.isEmpty()) continue;
                board.add(new BoardEntry(word, node.path("hot_value").asLong(0)));
            }
            return items(board, "douyin", observed, true, "抖音热榜");
        } catch (TrendSourceException e) {
            throw e;
        } catch (Exception e) {
            throw new TrendSourceException("抖音热榜响应无效");
        }
    }

    /** Weibo publishes data.realtime entries with word/note and an optional heat number. */
    static List<TrendItem> parseWeibo(String json, Instant observed) {
        try {
            JsonNode realtime = MAPPER.readTree(json).path("data").path("realtime");
            if (!realtime.isArray() || realtime.isEmpty()) throw new TrendSourceException("微博热搜响应无效");
            List<BoardEntry> board = new ArrayList<>();
            boolean hasHeatValues = true;
            for (JsonNode node : realtime) {
                String word = node.path("word").asText("").trim();
                if (word.isEmpty()) word = node.path("note").asText("").trim();
                if (word.isEmpty()) continue;
                if (node.hasNonNull("num") && node.path("num").canConvertToLong()) {
                    board.add(new BoardEntry(word, node.path("num").asLong()));
                } else {
                    hasHeatValues = false;
                    board.add(new BoardEntry(word, 0));
                }
            }
            return items(board, "weibo", observed, hasHeatValues, "微博热搜");
        } catch (TrendSourceException e) {
            throw e;
        } catch (Exception e) {
            throw new TrendSourceException("微博热搜响应无效");
        }
    }

    private record BoardEntry(String word, long heat) {}

    private static List<TrendItem> items(List<BoardEntry> board, String platform, Instant observed,
            boolean hasHeatValues, String boardName) {
        // Normalisation uses the whole board, not only the fashion entries, so the number keeps
        // meaning: the top word of the board scores 100 no matter how few entries are relevant.
        double max = Math.max(1, board.stream().mapToLong(BoardEntry::heat).max().orElse(1));
        Map<String, TrendItem> collected = new LinkedHashMap<>();
        int rank = 0;
        for (BoardEntry entry : board) {
            int position = rank++;
            if (!TrendTopics.boardFashion(entry.word())) continue;
            String label = hasHeatValues ? "平台热榜热度（归一化）" : "平台热榜位次";
            int score = hasHeatValues
                    ? (int) Math.min(100, Math.round(100 * Math.log1p(entry.heat()) / Math.log1p(max)))
                    : Math.max(1, (int) Math.round(100.0 * (board.size() - position) / board.size()));
            String summary = hasHeatValues
                    ? boardName + "第 " + (position + 1) + " 位 · 榜单热度值 " + String.format("%,d", entry.heat())
                    : boardName + "第 " + (position + 1) + " 位";
            String id = fingerprint(platform + "-hot:" + entry.word());
            collected.putIfAbsent(id, new TrendItem(id, platform, entry.word(),
                    TrendTopics.classify(entry.word()), score, observed, observed,
                    searchUrl(platform, entry.word()), false, null, summary,
                    new TrendEvidence(boardName, "board", List.of(), null, null, null, null, label, null)));
        }
        return List.copyOf(collected.values());
    }

    private static String searchUrl(String platform, String word) {
        String encoded = URLEncoder.encode(word, StandardCharsets.UTF_8).replace("+", "%20");
        return "douyin".equals(platform)
                ? "https://www.douyin.com/search/" + encoded
                : "https://s.weibo.com/weibo?q=" + encoded;
    }

    private static String fingerprint(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 24);
        } catch (Exception e) {
            throw new TrendSourceException("热榜条目无法处理");
        }
    }
}
