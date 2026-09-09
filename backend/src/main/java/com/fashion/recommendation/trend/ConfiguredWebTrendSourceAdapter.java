package com.fashion.recommendation.trend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Reads administrator-configured public HTML pages and turns their visible metadata into trend snapshots.
 *
 * <p>This deliberately does not pretend that arbitrary pages expose a platform heat score. The score is a
 * bounded freshness/completeness signal and is labelled separately in the API and UI.</p>
 */
@Component
@Order(20)
public class ConfiguredWebTrendSourceAdapter implements TrendSourceAdapter {
    static final String DEFAULT_PLATFORM = "web-scrape";
    static final String SCORE_LABEL = "来源页信号评分";
    private static final int MAX_URLS = 10;
    private static final int MAX_ITEMS = 50;
    private static final Pattern TAG_SPLITTER = Pattern.compile("[,，、|/·#]+");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final List<URI> sourceUrls;
    private final String platform;
    private final int maxPageChars;
    private final int maxArticlesPerPage;
    private final Cache<String, List<TrendItem>> cache;

    @Autowired
    public ConfiguredWebTrendSourceAdapter(
            ObjectMapper objectMapper,
            @Value("${app.trends.web-urls:}") String urls,
            @Value("${app.trends.web-platform:web-scrape}") String platform,
            @Value("${app.trends.web-max-page-chars:1000000}") int maxPageChars,
            @Value("${app.trends.web-max-articles-per-page:10}") int maxArticlesPerPage,
            @Value("${app.trends.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.trends.read-timeout:5s}") Duration readTimeout,
            @Value("${app.trends.cache-ttl:15m}") Duration cacheTtl) {
        this(createRestClient(connectTimeout, readTimeout), objectMapper, parseSourceUrls(urls), platform,
                maxPageChars, maxArticlesPerPage, cacheTtl);
    }

    ConfiguredWebTrendSourceAdapter(
            RestClient restClient,
            ObjectMapper objectMapper,
            List<URI> sourceUrls,
            String platform,
            int maxPageChars,
            int maxArticlesPerPage,
            Duration cacheTtl) {
        if (maxPageChars < 1_000 || maxPageChars > 5_000_000) {
            throw new TrendSourceException("网页趋势单页字符上限必须在 1000 到 5000000 之间");
        }
        if (maxArticlesPerPage < 1 || maxArticlesPerPage > 50) {
            throw new TrendSourceException("网页趋势单页条目上限必须在 1 到 50 之间");
        }
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.sourceUrls = List.copyOf(sourceUrls);
        this.platform = StringUtils.hasText(platform) ? platform.trim() : DEFAULT_PLATFORM;
        this.maxPageChars = maxPageChars;
        this.maxArticlesPerPage = maxArticlesPerPage;
        this.cache = Caffeine.newBuilder().maximumSize(1).expireAfterWrite(cacheTtl).build();
    }

    @Override
    public String platform() {
        return platform;
    }

    @Override
    public String scoreLabel() {
        return SCORE_LABEL;
    }

    @Override
    public List<TrendItem> fetchPublicSnapshots() {
        if (sourceUrls.isEmpty()) {
            return List.of();
        }
        return cache.get("web-pages", ignored -> fetchAndParse());
    }

    private List<TrendItem> fetchAndParse() {
        Instant fetchedAt = Instant.now();
        List<TrendItem> items = new ArrayList<>();
        for (URI sourceUrl : sourceUrls) {
            try {
                ensurePublicAddress(sourceUrl);
                String html = restClient.get()
                        .uri(sourceUrl)
                        .accept(MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML)
                        .retrieve()
                        .body(String.class);
                if (!StringUtils.hasText(html)) {
                    throw new TrendSourceException("网页趋势源返回空内容");
                }
                if (html.length() > maxPageChars) {
                    throw new TrendSourceException("网页趋势源响应超过单页字符上限");
                }
                items.addAll(parsePage(sourceUrl, html, fetchedAt));
            } catch (RuntimeException ignored) {
                // One unavailable page must not hide other configured pages. If all fail, fail closed below.
            }
            if (items.size() >= MAX_ITEMS) {
                break;
            }
        }
        if (items.isEmpty()) {
            throw new TrendSourceException("所有配置的网页趋势源均不可用或没有可识别内容");
        }
        return List.copyOf(items.subList(0, Math.min(MAX_ITEMS, items.size())));
    }

    private List<TrendItem> parsePage(URI sourceUrl, String html, Instant fetchedAt) {
        Document document = Jsoup.parse(html, sourceUrl.toString());
        PageMetadata page = extractPageMetadata(document);
        List<Element> candidates = articleCandidates(document);
        List<TrendItem> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Element candidate : candidates) {
            String title = firstNonBlank(
                    heading(candidate),
                    page.title(),
                    "未命名网页趋势");
            String source = candidate == document ? sourceUrl.toString() : firstLinkUrl(candidate, sourceUrl, sourceUrl.toString());
            String image = firstHttpUrl(candidate, sourceUrl, page.imageUrl());
            String summary = limitText(firstNonBlank(description(candidate), page.description(), null), 600);
            List<String> tags = extractTags(candidate, page.tags());
            Instant publishedAt = parsePublishedAt(firstNonBlank(dateValue(candidate), page.publishedAt(), null), fetchedAt);
            String id = stableId(source, title);
            if (ids.add(id)) {
                items.add(new TrendItem(id, platform, limitText(title, 200), tags,
                        sourceSignalScore(publishedAt, fetchedAt, image != null, summary != null, tags.size()),
                        publishedAt, fetchedAt, source, false, image, summary));
            }
        }
        return items;
    }

    private List<Element> articleCandidates(Document document) {
        List<Element> articles = document.select("article").stream()
                .filter(candidate -> StringUtils.hasText(heading(candidate)))
                .limit(maxArticlesPerPage)
                .toList();
        return articles.size() >= 2 ? articles : List.of(document);
    }

    private PageMetadata extractPageMetadata(Document document) {
        JsonNode jsonLd = findArticleJsonLd(document);
        String title = firstNonBlank(
                firstMeta(document, "property", "og:title"),
                firstMeta(document, "name", "twitter:title"),
                jsonText(jsonLd, "headline"),
                document.title(),
                document.select("h1").stream().findFirst().map(Element::text).orElse(null));
        String description = firstNonBlank(
                firstMeta(document, "name", "description"),
                firstMeta(document, "property", "og:description"),
                jsonText(jsonLd, "description"));
        String image = firstHttpUrl(document, URI.create(document.baseUri()), firstNonBlank(
                firstMeta(document, "property", "og:image"),
                jsonImage(jsonLd, "image"),
                firstImage(document)));
        String publishedAt = firstNonBlank(
                firstMeta(document, "property", "article:published_time"),
                firstMeta(document, "name", "datePublished"),
                jsonText(jsonLd, "datePublished"),
                document.select("time[datetime]").stream().findFirst().map(element -> element.attr("datetime")).orElse(null));
        List<String> tags = new ArrayList<>();
        tags.addAll(splitTags(firstMeta(document, "name", "keywords")));
        tags.addAll(splitTags(firstMeta(document, "property", "article:tag")));
        tags.addAll(jsonKeywords(jsonLd));
        return new PageMetadata(title, description, image, publishedAt, normalizeTags(tags));
    }

    private JsonNode findArticleJsonLd(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            String data = script.data();
            if (!StringUtils.hasText(data)) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(data);
                JsonNode match = findJsonNode(root);
                if (match != null) {
                    return match;
                }
            } catch (Exception ignored) {
                // Invalid JSON-LD should not make otherwise readable HTML unusable.
            }
        }
        return null;
    }

    private static JsonNode findJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject() && (node.has("headline") || node.has("datePublished") || node.has("articleBody"))) {
            return node;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode match = findJsonNode(child);
                if (match != null) {
                    return match;
                }
            }
        } else if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                JsonNode match = findJsonNode(fields.next().getValue());
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static String heading(Element candidate) {
        return candidate.select("h1, h2, h3, [itemprop=headline]").stream()
                .map(Element::text)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private static String description(Element candidate) {
        return candidate.select("[itemprop=description], p").stream()
                .map(Element::text)
                .map(String::trim)
                .filter(text -> text.length() >= 20)
                .findFirst()
                .orElse(null);
    }

    private static String dateValue(Element candidate) {
        return candidate.select("[itemprop=datePublished], time[datetime], [class*=date]").stream()
                .map(element -> firstNonBlank(element.attr("datetime"), element.text(), null))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private static String firstImage(Element candidate) {
        return candidate.select("img").stream()
                .map(image -> firstNonBlank(image.absUrl("src"), image.absUrl("data-src"), image.absUrl("data-original"), null))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private static String firstHttpUrl(Element candidate, URI baseUrl, String fallback) {
        String value = firstNonBlank(firstImage(candidate), fallback, null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            URI resolved = URI.create(value).isAbsolute() ? URI.create(value) : baseUrl.resolve(value);
            if (("http".equalsIgnoreCase(resolved.getScheme()) || "https".equalsIgnoreCase(resolved.getScheme()))
                    && StringUtils.hasText(resolved.getHost())) {
                return resolved.toString();
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid image/link metadata is optional and is ignored.
        }
        return null;
    }

    private static String firstLinkUrl(Element candidate, URI baseUrl, String fallback) {
        String value = candidate.select("a[href]").stream()
                .map(link -> link.absUrl("href"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(fallback);
        try {
            URI resolved = URI.create(value).isAbsolute() ? URI.create(value) : baseUrl.resolve(value);
            if (("http".equalsIgnoreCase(resolved.getScheme()) || "https".equalsIgnoreCase(resolved.getScheme()))
                    && StringUtils.hasText(resolved.getHost())) {
                return resolved.toString();
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid article links are optional and fall back to the configured page.
        }
        return fallback;
    }

    private static List<String> extractTags(Element candidate, List<String> pageTags) {
        List<String> tags = new ArrayList<>(pageTags);
        for (Element element : candidate.select("a[rel=tag], [class*=tag] a, [class*=tag]")) {
            tags.addAll(splitTags(element.text()));
        }
        List<String> normalized = normalizeTags(tags);
        return normalized.isEmpty() ? List.of("网页趋势") : normalized;
    }

    private static List<String> splitTags(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String candidate : TAG_SPLITTER.split(value)) {
            String tag = candidate.trim();
            if (StringUtils.hasText(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private static List<String> normalizeTags(List<String> values) {
        List<String> tags = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            String tag = limitText(value, 40);
            if (StringUtils.hasText(tag) && seen.add(tag.toLowerCase(Locale.ROOT))) {
                tags.add(tag);
            }
            if (tags.size() == 10) {
                break;
            }
        }
        return List.copyOf(tags);
    }

    private static Instant parsePublishedAt(String value, Instant fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return ZonedDateTime.parse(value).toInstant();
                } catch (DateTimeParseException ignoredThird) {
                    try {
                        return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
                    } catch (DateTimeParseException ignoredLast) {
                        return fallback;
                    }
                }
            }
        }
    }

    private static int sourceSignalScore(Instant publishedAt, Instant fetchedAt, boolean hasImage,
                                         boolean hasSummary, int tagCount) {
        long ageHours = Math.max(0, Duration.between(publishedAt, fetchedAt).toHours());
        int recency = ageHours <= 24 ? 30 : ageHours <= 72 ? 24 : ageHours <= 168 ? 18 : ageHours <= 720 ? 10 : 4;
        int completeness = (hasImage ? 8 : 0) + (hasSummary ? 7 : 0) + Math.min(tagCount, 5);
        return Math.min(100, 40 + recency + completeness);
    }

    private static String stableId(String source, String title) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((source + "\n" + title).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("web-");
            for (int index = 0; index < 12; index++) {
                hex.append(String.format("%02x", digest[index]));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new TrendSourceException("无法生成网页趋势 ID", exception);
        }
    }

    private static String firstMeta(Document document, String attribute, String value) {
        return document.select("meta[" + attribute + "=" + value + "]").stream()
                .map(element -> element.attr("content"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private static String jsonText(JsonNode node, String field) {
        if (node == null || !node.path(field).isTextual()) {
            return null;
        }
        return node.path(field).textValue();
    }

    private static String jsonImage(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isArray() && !value.isEmpty()) {
            JsonNode first = value.get(0);
            return first.isTextual() ? first.textValue() : first.path("url").asText(null);
        }
        return value.path("url").asText(null);
    }

    private static List<String> jsonKeywords(JsonNode node) {
        if (node == null) {
            return List.of();
        }
        JsonNode value = node.path("keywords");
        if (value.isTextual()) {
            return splitTags(value.textValue());
        }
        if (value.isArray()) {
            List<String> tags = new ArrayList<>();
            value.forEach(item -> {
                if (item.isTextual()) {
                    tags.addAll(splitTags(item.textValue()));
                }
            });
            return tags;
        }
        return List.of();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String limitText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static List<URI> parseSourceUrls(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String[] values = raw.split("[,\\r\\n]+");
        if (values.length > MAX_URLS) {
            throw new TrendSourceException("网页趋势源 URL 数量不能超过 " + MAX_URLS);
        }
        List<URI> urls = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            String trimmed = value.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            try {
                URI uri = URI.create(trimmed);
                validateConfiguredUrl(uri);
                if (seen.add(uri.toString())) {
                    urls.add(uri);
                }
            } catch (IllegalArgumentException exception) {
                throw new TrendSourceException("网页趋势源 URL 不合法", exception);
            }
        }
        return List.copyOf(urls);
    }

    private static void validateConfiguredUrl(URI uri) {
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null) {
            throw new TrendSourceException("网页趋势源只允许不带用户信息的 HTTP(S) URL");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".internal") || isLiteralPrivateAddress(host)) {
            throw new TrendSourceException("网页趋势源不能指向本机或内网地址");
        }
    }

    private static void ensurePublicAddress(URI uri) {
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isPrivateAddress(address)) {
                    throw new TrendSourceException("网页趋势源解析到了本机或内网地址");
                }
            }
        } catch (UnknownHostException exception) {
            throw new TrendSourceException("网页趋势源域名无法解析", exception);
        }
    }

    private static boolean isLiteralPrivateAddress(String host) {
        if (!(host.contains(":") || host.matches("[0-9.]+"))) {
            return false;
        }
        try {
            return isPrivateAddress(InetAddress.getByName(host.replace("[", "").replace("]", "")));
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static boolean isPrivateAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        boolean ipv6UniqueLocal = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        boolean ipv6LinkLocal = bytes.length == 16 && (bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xc0) == 0x80;
        return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress() || ipv6UniqueLocal || ipv6LinkLocal;
    }

    private static RestClient createRestClient(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "fashion-recommendation-trend/0.1")
                .build();
    }

    private record PageMetadata(String title, String description, String imageUrl, String publishedAt, List<String> tags) {
    }
}
