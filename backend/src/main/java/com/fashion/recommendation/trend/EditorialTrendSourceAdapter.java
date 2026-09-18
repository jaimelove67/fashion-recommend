package com.fashion.recommendation.trend;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publisher RSS feeds with editorial recency, never social popularity.
 *
 * <p>Feeds are admin-configured HTTPS publisher feeds. Each feed is fetched independently, so one
 * unavailable publication never removes another publication's items. Items keep the publisher's
 * own title, link, publication time and image; no heat value is invented for editorial content.
 */
@Component
public class EditorialTrendSourceAdapter implements TrendSourceAdapter {
    /** Used when no feed list is configured, so an enabled editorial source always has a target. */
    static final String DEFAULT_FEED = "https://www.vogue.com/feed/rss";
    private static final String PLATFORM = "editorial";
    private static final String SCORE_LABEL = "编辑发布 · 按时间";
    private static final String USER_AGENT = "FashionResearch/1.0";
    private static final int MAX_BODY_BYTES = 2_000_000;
    private static final int FETCH_TIMEOUT_MS = 12_000;
    private static final int MAX_TITLE_CHARS = 200;
    private static final int MAX_SUMMARY_CHARS = 160;
    private static final int MAX_ID_CHARS = 24;

    private final boolean enabled;
    private final List<String> feeds;

    public EditorialTrendSourceAdapter(
            @Value("${app.trends.editorial-enabled:false}") boolean enabled,
            @Value("${app.trends.editorial-feeds:}") String feeds) {
        this.enabled = enabled;
        this.feeds = parseFeeds(feeds);
    }

    @Override public String platform() { return PLATFORM; }
    @Override public String scoreLabel() { return SCORE_LABEL; }

    @Override
    public List<TrendItem> fetchPublicSnapshots() {
        if (!enabled) return List.of();
        Instant now = Instant.now();
        Map<String, TrendItem> collected = new LinkedHashMap<>();
        int failed = 0;
        for (String feed : feeds) {
            try {
                for (TrendItem item : parse(download(feed), now)) collected.putIfAbsent(item.id(), item);
            } catch (Exception e) {
                // A blocked or malformed publication must not discard the feeds that did respond.
                failed++;
            }
        }
        if (collected.isEmpty() && failed > 0) throw new TrendSourceException("公开时尚订阅源暂不可用");
        return List.copyOf(collected.values());
    }

    /** Only HTTPS publisher feeds are accepted; a blank configuration keeps the built-in feed. */
    static List<String> parseFeeds(String configured) {
        if (configured == null || configured.isBlank()) return List.of(DEFAULT_FEED);
        List<String> feeds = Arrays.stream(configured.split("[,\\r\\n]+"))
                .map(String::trim)
                .filter(value -> value.startsWith("https://") && isHttps(value))
                .distinct()
                .toList();
        return feeds.isEmpty() ? List.of(DEFAULT_FEED) : feeds;
    }

    private static String download(String feed) {
        try {
            return Jsoup.connect(feed).userAgent(USER_AGENT).timeout(FETCH_TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES).ignoreContentType(true).execute().body();
        } catch (Exception e) {
            throw new TrendSourceException("公开时尚订阅源暂不可用");
        }
    }

    static List<TrendItem> parse(String xml, Instant now) {
        Map<String, TrendItem> items = new LinkedHashMap<>();
        for (Element node : Jsoup.parse(xml, "", Parser.xmlParser()).select("item")) {
            try {
                String title = text(node, "title");
                if (title.isBlank()) continue;
                String link = text(node, "link");
                if (!isPublicUrl(link)) continue;
                String rawDescription = text(node, "description");
                // Some feeds leave the body empty and publish the article inside content:encoded only.
                String rawContent = text(node, "content:encoded");
                String description = Jsoup.parse(rawDescription.isBlank() ? rawContent : rawDescription).text().trim();
                if (!TrendTopics.fashion(title + " " + description)) continue;
                Instant published = publishedAt(text(node, "pubDate"));
                if (published == null || published.isAfter(now.plusSeconds(60))) continue;
                String image = image(node, rawDescription, rawContent);
                String creator = firstNonBlank(text(node, "dc:creator"), text(node, "author"));
                String id = fingerprint(link);
                // A repeated link is the same article; counting it twice would inflate source totals.
                items.putIfAbsent(id, new TrendItem(
                        id, PLATFORM, truncate(title, MAX_TITLE_CHARS),
                        TrendTopics.classify(title), 0, published, now, link, false, image,
                        truncate(description, MAX_SUMMARY_CHARS),
                        new TrendEvidence(creator.isBlank() ? "编辑精选" : creator, "article",
                                image == null ? List.of() : List.of(image), null, null, null, null,
                                SCORE_LABEL, null)));
            } catch (Exception ignored) { /* Isolate malformed RSS entries. */ }
        }
        return List.copyOf(items.values());
    }

    private static String text(Element node, String tag) {
        Element element = node.getElementsByTag(tag).first();
        return element == null ? "" : element.text().trim();
    }

    private static Instant publishedAt(String value) {
        if (value.isBlank()) return null;
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static String image(Element node, String... htmlSources) {
        for (String tag : List.of("media:content", "media:thumbnail", "enclosure")) {
            Element element = node.getElementsByTag(tag).first();
            if (element == null) continue;
            if (tag.equals("enclosure") && !element.attr("type").startsWith("image/")) continue;
            String url = element.attr("url").trim();
            if (isHttps(url)) return displaySize(url);
        }
        for (String html : htmlSources) {
            if (html == null || html.isBlank()) continue;
            Element inline = Jsoup.parse(html).selectFirst("img[src]");
            String url = inline == null ? "" : inline.attr("src").trim();
            if (isHttps(url)) return displaySize(url);
        }
        return null;
    }

    /** Publisher CDNs serve full-size masters; request a display width instead of the raw asset. */
    private static String displaySize(String url) {
        return url.startsWith("https://assets.vogue.com/photos/")
                ? url.replace("/master/pass/", "/w_800,c_limit/") : url;
    }

    /**
     * Publisher article links may still be plain HTTP (for example 理想生活实验室 publishes http
     * permalinks although its host serves https). An outbound article link is safe to keep, but
     * anything without a host, carrying user-info, or using a non-web scheme is rejected.
     */
    private static boolean isPublicUrl(String url) {
        for (String scheme : List.of("https://", "http://")) {
            if (url != null && url.startsWith(scheme)) return hasHost(url.substring(scheme.length()));
        }
        return false;
    }

    /** Images must be HTTPS so an https page never loads mixed content. */
    private static boolean isHttps(String url) {
        return url != null && url.startsWith("https://") && hasHost(url.substring("https://".length()));
    }

    private static boolean hasHost(String remainder) {
        int slash = remainder.indexOf('/');
        String host = slash < 0 ? remainder : remainder.substring(0, slash);
        return !host.isBlank() && !host.contains("@") && !host.contains(" ");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String fingerprint(String url) throws Exception {
        return java.util.HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)))
                .substring(0, MAX_ID_CHARS);
    }
}
