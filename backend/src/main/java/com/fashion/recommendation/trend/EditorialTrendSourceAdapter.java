package com.fashion.recommendation.trend;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Publisher RSS with editorial recency, never social popularity. */
@Component
public class EditorialTrendSourceAdapter implements TrendSourceAdapter {
    private final boolean enabled;
    public EditorialTrendSourceAdapter(@Value("${app.trends.editorial-enabled:false}") boolean enabled) { this.enabled = enabled; }
    @Override public String platform() { return "vogue-rss"; }
    @Override public String scoreLabel() { return "编辑发布 · 按时间"; }
    @Override public List<TrendItem> fetchPublicSnapshots() {
        if (!enabled) return List.of();
        try {
            String xml = Jsoup.connect("https://www.vogue.com/feed/rss").userAgent("FashionResearch/1.0")
                    .timeout(12000).maxBodySize(2_000_000).ignoreContentType(true).execute().body();
            return parse(xml, Instant.now());
        } catch (Exception e) { throw new TrendSourceException("公开时尚订阅源暂不可用"); }
    }
    static List<TrendItem> parse(String xml, Instant now) {
        List<TrendItem> items = new ArrayList<>();
        for (Element node : Jsoup.parse(xml, "", Parser.xmlParser()).select("item")) {
            try {
                String title = node.selectFirst("title").text();
                String category = node.select("category").text().toLowerCase();
                if (!category.contains("fashion") && !category.contains("shopping")) continue;
                if (!TrendTopics.fashion(title)) continue;
                String url = node.selectFirst("link").text();
                if (!url.startsWith("https://www.vogue.com/")) continue;
                Instant published = ZonedDateTime.parse(node.selectFirst("pubDate").text(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Element media = node.getElementsByTag("media:content").first();
                if (media == null || media.attr("url").isBlank()) media = node.getElementsByTag("media:thumbnail").first();
                String image = media == null ? null : media.attr("url");
                if (image != null && !image.startsWith("https://")) image = null;
                if (image != null && image.startsWith("https://assets.vogue.com/photos/"))
                    image = image.replace("/master/pass/", "/w_800,c_limit/");
                String description = node.selectFirst("description") == null ? "" : Jsoup.parse(node.selectFirst("description").text()).text();
                description = description.substring(0, Math.min(160, description.length()));
                String id = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
                Element creator = node.getElementsByTag("dc:creator").first();
                items.add(new TrendItem(id, "editorial", title.substring(0, Math.min(200, title.length())),
                        TrendTopics.classify(title), 0, published, now, url, false, image, description,
                        new TrendEvidence(creator == null ? "Vogue" : creator.text(), "article", image == null ? List.of() : List.of(image),
                                null, null, null, null, "编辑发布 · 按时间", null)));
            } catch (Exception ignored) { /* Isolate malformed RSS entries. */ }
        }
        return List.copyOf(items);
    }
}
