package com.fashion.recommendation.technique;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bounded public-publisher discovery, followed by a separate read of each article's body. */
@Component
public class TechniqueSourceCollector {
    static final String DEFAULT_FEEDS = "https://www.permanentstyle.com/category/style/feed,https://putthison.com/tag/guides/feed/";
    static final int MAX_BODY_BYTES = 1_500_000;
    static final int MAX_ARTICLE_CHARS = 24_000;
    static final int MAX_ARTICLES = 6;
    static final int MAX_CANDIDATES = 20;
    private static final int MAX_SOURCES = 6;
    private static final int MAX_REDIRECTS = 3;
    private static final int FETCH_TIMEOUT_MS = 6_000;
    private static final long COLLECTION_BUDGET_MS = 90_000;
    private static final Logger log = LoggerFactory.getLogger(TechniqueSourceCollector.class);
    private static final Pattern PROMOTION = Pattern.compile(
            "(?i)inside track|shopping guide|discount|\\bsale\\b|\\brestock|\\bfragrance\\b|\\bperfume\\b|"
                    + "ask me anything|product design|new knitwear|\\breview\\b|trunk show|"
                    + "darn it|fix holes|\\brepair\\b|\\bcleaning\\b|\\bmaintenance\\b|"
                    + "sponsored|gift guide|shop (?:the|our)|new collection|购物清单|折扣|广告|促销|香水");
    private static final Pattern DIRECT_METHOD = Pattern.compile(
            "(?i)how to|ways to|guide to|guide on|wardrobe|colour|color|layering|proportion|combinations|如何|搭配|配色|叠穿");
    private static final Pattern FASHION = Pattern.compile(
            "(?i)\\b(wear|wearing|outfit|clothes|clothing|trouser|shirt|jacket|knitwear|sweater|dress|"
                    + "tailoring|wardrobe|shoe|trainer|silhouette|layering|menswear|womenswear)s?\\b|穿搭|服装|衣服|叠穿|裤|裙|鞋");
    private static final Pattern METHOD = Pattern.compile(
            "(?i)\\b(how|fit|proportion|balance|contrast|texture|layer|layering|pair|combine|colour|color|"
                    + "silhouette|formal|casual|dressing|length|volume)\\b|搭配|配色|比例|轮廓|叠穿|松量|版型|裤长");

    private final ObjectMapper mapper;
    private final List<URI> feeds;
    private final List<URI> articleUrls;
    private final Set<String> allowedHosts;
    private final PageFetcher fetcher;
    private final AddressResolver resolver;
    private final Clock clock;

    @Autowired
    public TechniqueSourceCollector(ObjectMapper mapper,
            @Value("${app.techniques.feeds:" + DEFAULT_FEEDS + "}") String feeds,
            @Value("${app.techniques.article-urls:}") String articleUrls) {
        this(mapper, configuredUrls(feeds), configuredUrls(articleUrls),
                TechniqueSourceCollector::download, InetAddress::getAllByName, Clock.systemUTC());
    }

    TechniqueSourceCollector(ObjectMapper mapper, List<URI> feeds, List<URI> articleUrls,
            PageFetcher fetcher, AddressResolver resolver, Clock clock) {
        if (feeds.size() > MAX_SOURCES || articleUrls.size() > 12) {
            throw new TechniqueSourceException("穿搭技巧来源数量超出上限");
        }
        this.mapper = mapper;
        this.feeds = List.copyOf(feeds);
        this.articleUrls = List.copyOf(articleUrls);
        this.allowedHosts = new LinkedHashSet<>();
        for (URI source : concat(feeds, articleUrls)) {
            validateUri(source);
            allowedHosts.add(baseHost(source.getHost()));
        }
        this.fetcher = fetcher;
        this.resolver = resolver;
        this.clock = clock;
    }

    public List<TechniqueArticle> collect() {
        long deadline = System.nanoTime() + COLLECTION_BUDGET_MS * 1_000_000;
        List<List<Candidate>> discovered = new ArrayList<>();
        for (URI feed : feeds) {
            if (System.nanoTime() >= deadline) break;
            try {
                Page page = fetch(feed);
                discovered.add(rotateForWeek(discover(page)));
            } catch (Exception e) {
                log.info("Technique discovery unavailable for host {}", feed.getHost());
            }
        }
        // Interleave publishers so one prolific feed cannot occupy the entire read budget.
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (int i = 0; i < MAX_CANDIDATES; i++) {
            for (List<Candidate> source : discovered) {
                if (i < source.size() && candidates.size() < MAX_CANDIDATES) add(candidates, source.get(i));
            }
        }
        // Configured evergreen articles also get a chance even when feeds are crowded.
        Map<String, Candidate> ordered = new LinkedHashMap<>();
        for (URI uri : articleUrls) add(ordered, new Candidate(uri, null, null, null, null));
        candidates.forEach(ordered::putIfAbsent);
        Map<String, TechniqueArticle> articles = new LinkedHashMap<>();
        int attempted = 0;
        for (Candidate candidate : ordered.values()) {
            if (attempted++ >= MAX_CANDIDATES || articles.size() >= MAX_ARTICLES
                    || System.nanoTime() >= deadline) break;
            try {
                TechniqueArticle article = readArticle(fetch(candidate.url()), candidate);
                if (article != null) articles.putIfAbsent(article.id(), article);
            } catch (Exception e) {
                log.debug("Technique article unavailable for host {}", candidate.url().getHost());
            }
        }
        if (articles.isEmpty()) throw new TechniqueSourceException("未取得可核读的完整穿搭教程正文；本周不发布新内容");
        return List.copyOf(articles.values());
    }

    private List<Candidate> rotateForWeek(List<Candidate> candidates) {
        if (candidates.isEmpty()) return candidates;
        LocalDate localDate = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai"));
        int week = localDate.get(WeekFields.ISO.weekBasedYear()) * 53
                + localDate.get(WeekFields.ISO.weekOfWeekBasedYear()) - 1;
        int offset = Math.floorMod(week, candidates.size());
        List<Candidate> rotated = new ArrayList<>(candidates.size());
        rotated.addAll(candidates.subList(offset, candidates.size()));
        rotated.addAll(candidates.subList(0, offset));
        return rotated;
    }

    private List<Candidate> discover(Page page) {
        Document xml = Jsoup.parse(new String(page.body(), StandardCharsets.UTF_8), page.url().toString(), Parser.xmlParser());
        List<Candidate> candidates = new ArrayList<>();
        String publisher = first(text(xml, "channel > title"), text(xml, "feed > title"));
        for (Element entry : xml.select("item, entry")) {
            String title = text(entry, "title");
            if (title == null || PROMOTION.matcher(title).find()) continue;
            Element alternate = entry.selectFirst("link[href]:not([rel]), link[rel=alternate][href]");
            String link = alternate == null ? text(entry, "link") : alternate.attr("href");
            URI uri = safeLink(page.url(), link);
            if (uri == null) continue;
            String author = first(tagText(entry, "dc:creator"), text(entry, "author > name"), text(entry, "author"));
            String date = validDate(first(text(entry, "pubDate"), text(entry, "published")));
            candidates.add(new Candidate(uri, title, author, publisher, date));
            if (candidates.size() >= MAX_CANDIDATES) break;
        }
        if (!candidates.isEmpty()) {
            candidates.sort(Comparator.comparingInt((Candidate c) -> DIRECT_METHOD.matcher(c.title()).find() ? 1 : 0).reversed());
            return candidates;
        }
        Document html = parseHtml(page);
        for (Element link : html.select("article h2 a[href], article h3 a[href], main a[href], .entry-content a[href]")) {
            String title = link.text().trim();
            if (title.length() < 12 || PROMOTION.matcher(title).find()
                    || !FASHION.matcher(title).find() || !METHOD.matcher(title).find()) continue;
            URI uri = safeLink(page.url(), link.attr("href"));
            if (uri != null) candidates.add(new Candidate(uri, title, null, null, null));
            if (candidates.size() >= MAX_CANDIDATES) break;
        }
        return candidates;
    }

    private TechniqueArticle readArticle(Page page, Candidate candidate) {
        if (!page.contentType().toLowerCase(Locale.ROOT).contains("html")) return null;
        Document html = parseHtml(page);
        String robots = html.select("meta[name=robots],meta[name=googlebot],meta[name=ai]")
                .stream().map(e -> e.attr("content")).reduce(page.robots(), (a, b) -> a + " " + b).toLowerCase(Locale.ROOT);
        if (robots.matches("(?s).*\\b(noai|noarchive|nosnippet)\\b.*")
                || !html.select(".paywall, .subscription-required, #paywall, [data-paywall], form[action*=login]").isEmpty()) return null;
        JsonNode article = articleMetadata(html);
        String title = first(heading(html), jsonText(article, "headline"), candidate.title(), meta(html, "property", "og:title"));
        if (title == null || title.length() > 250 || PROMOTION.matcher(title).find()) return null;
        html.select("script,style,nav,footer,aside,form,button,.comments,#comments,.comment-list,"
                + ".related-posts,.related-articles,.sharedaddy,.advertisement,.advert,.newsletter,"
                + "[data-nosnippet],[hidden],[aria-hidden=true]").remove();
        Element body = null;
        for (String selector : List.of("[itemprop=articleBody]", ".entry-content", ".post-content", ".article-body", ".article-content", "article")) {
            Element found = html.selectFirst(selector);
            if (found != null && found.text().length() >= 1_200) { body = found; break; }
        }
        if (body == null) return null;
        List<String> paragraphs = bodyParagraphs(body);
        String content = String.join("\n\n", paragraphs);
        // Never count feed excerpts, clipped bodies, product cards or almost-empty pages as a full read.
        if (paragraphs.stream().filter(p -> p.length() >= 80).count() < 5
                || content.length() < 1_200 || content.length() > MAX_ARTICLE_CHARS
                || !FASHION.matcher(title + " " + content).find() || !METHOD.matcher(title + " " + content).find()) return null;
        String lower = content.toLowerCase(Locale.ROOT);
        if (lower.contains("subscribe to continue reading") || lower.contains("sign in to continue reading")
                || lower.contains("this content is for subscribers") || lower.contains("purchase to continue")) return null;
        URI canonical = page.url();
        Element canonicalLink = html.selectFirst("link[rel=canonical][href]");
        if (canonicalLink != null) {
            URI resolved = safeLink(page.url(), canonicalLink.attr("href"));
            if (resolved != null) canonical = resolved;
        }
        String url = normalize(canonical).toString();
        String author = first(meta(html, "name", "author"), person(article == null ? null : article.get("author")),
                text(html, "[rel=author]"), candidate.author());
        String platform = first(meta(html, "property", "og:site_name"),
                person(article == null ? null : article.get("publisher")), candidate.platform(), page.url().getHost());
        String date = validDate(first(meta(html, "property", "article:published_time"),
                jsonText(article, "datePublished"), meta(html, "name", "datePublished"), candidate.date()));
        return new TechniqueArticle(fingerprint(url), title, url, limit(author, 200), limit(platform, 200), date, clock.instant(), content);
    }

    private static List<String> bodyParagraphs(Element body) {
        StringBuilder text = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override public void head(Node node, int depth) {
                if (node instanceof Element e && (e.tag().isBlock() || e.tagName().equals("br"))) text.append('\n');
                if (node instanceof TextNode t) text.append(t.getWholeText());
            }
            @Override public void tail(Node node, int depth) {
                if (node instanceof Element e && e.tag().isBlock()) text.append('\n');
            }
        }, body);
        // Preserve short headings and qualifiers; quality is checked separately by substantial paragraphs.
        return Arrays.stream(text.toString().replace('\u00a0', ' ').split("\\n+"))
                .map(p -> p.replaceAll("[\\t\\r\\f ]+", " ").trim()).filter(p -> !p.isBlank()).toList();
    }

    private Page fetch(URI uri) throws Exception {
        URI target = normalize(uri);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            ensurePublicTarget(target);
            Page response = fetcher.fetch(target);
            if (response.status() >= 300 && response.status() < 400) {
                if (redirects == MAX_REDIRECTS || response.location() == null) throw new TechniqueSourceException("来源重定向异常");
                URI next = safeLink(target, response.location());
                if (next == null) throw new TechniqueSourceException("来源重定向超出许可范围");
                target = next;
                continue;
            }
            if (response.status() != 200 || response.body().length > MAX_BODY_BYTES) throw new TechniqueSourceException("来源响应不可用或过大");
            String type = response.contentType().toLowerCase(Locale.ROOT);
            if (!(type.contains("html") || type.contains("xml") || type.contains("rss") || type.contains("atom"))) {
                throw new TechniqueSourceException("来源并非公开文章或订阅源");
            }
            return new Page(target, response.status(), response.contentType(), response.body(), null, response.robots());
        }
        throw new TechniqueSourceException("来源重定向过多");
    }

    private void ensurePublicTarget(URI uri) throws Exception {
        validateUri(uri);
        if (!allowedHost(uri.getHost())) throw new TechniqueSourceException("来源域名不在配置范围内");
        InetAddress[] addresses = resolver.resolve(uri.getHost());
        if (addresses == null || addresses.length == 0) throw new TechniqueSourceException("来源域名无法解析");
        for (InetAddress address : addresses) {
            byte[] raw = address.getAddress();
            int first = raw[0] & 255;
            int second = raw[1] & 255;
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()
                    || (raw.length == 4 && (first == 0 || first == 127 || first >= 224
                        || (first == 100 && second >= 64 && second <= 127)
                        || (first == 169 && second == 254) || (first == 192 && second == 0)
                        || (first == 198 && (second == 18 || second == 19))))
                    || (raw.length == 16 && ((first & 0xfe) == 0xfc || (first & 0xe0) != 0x20))) {
                throw new TechniqueSourceException("穿搭技巧来源必须解析为公网地址");
            }
        }
    }

    private boolean allowedHost(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return allowedHosts.stream().anyMatch(root -> lower.equals(root) || lower.endsWith("." + root));
    }

    private URI safeLink(URI base, String link) {
        if (link == null || link.isBlank()) return null;
        try {
            URI uri = normalize(base.resolve(link.trim()));
            validateUri(uri);
            return allowedHost(uri.getHost()) ? uri : null;
        } catch (RuntimeException e) { return null; }
    }

    private static Page download(URI uri) throws Exception {
        // Automatic redirects are disabled: every hop repeats scheme, host and public-DNS checks.
        // DNS is checked immediately before Jsoup connects; deployment egress rules remain the
        // network boundary against a DNS authority that changes answers between lookup and connect.
        var response = Jsoup.connect(uri.toString()).userAgent("FashionTechniqueResearch/1.0")
                .timeout(FETCH_TIMEOUT_MS).maxBodySize(MAX_BODY_BYTES + 1).followRedirects(false)
                .ignoreContentType(true).ignoreHttpErrors(true).header("Accept", "text/html,application/rss+xml,application/atom+xml,application/xml")
                .execute();
        return new Page(uri, response.statusCode(), first(response.contentType(), "application/octet-stream"),
                response.bodyAsBytes(), response.header("Location"), response.header("X-Robots-Tag"));
    }

    private Document parseHtml(Page page) {
        try { return Jsoup.parse(new ByteArrayInputStream(page.body()), null, page.url().toString()); }
        catch (Exception e) { throw new TechniqueSourceException("文章正文无法解析"); }
    }

    private JsonNode articleMetadata(Document html) {
        for (Element script : html.select("script[type=application/ld+json]")) {
            try {
                JsonNode found = findArticle(mapper.readTree(script.data()));
                if (found != null) return found;
            } catch (Exception ignored) { /* Invalid optional metadata does not invalidate visible text. */ }
        }
        return null;
    }

    private static JsonNode findArticle(JsonNode node) {
        if (node == null) return null;
        if (node.isObject()) {
            String type = node.path("@type").toString();
            if (type.contains("Article") || type.contains("BlogPosting")) return node;
            return findArticle(node.get("@graph"));
        }
        if (node.isArray()) for (JsonNode child : node) { JsonNode result = findArticle(child); if (result != null) return result; }
        return null;
    }

    private static String person(JsonNode node) {
        if (node == null) return null;
        if (node.isTextual()) return node.asText();
        if (node.isObject()) return jsonText(node, "name");
        if (node.isArray()) return node.isEmpty() ? null : person(node.get(0));
        return null;
    }

    private static void validateUri(URI uri) {
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443) || !host.contains(".")
                || host.matches("[0-9.]+") || host.contains(":") || host.endsWith(".local")
                || host.endsWith(".localhost") || host.endsWith(".internal")) {
            throw new TechniqueSourceException("穿搭技巧来源必须是无凭据的公开 HTTPS 文章地址");
        }
    }

    static URI normalize(URI uri) {
        String query = uri.getRawQuery();
        if (query != null) {
            query = Arrays.stream(query.split("&")).filter(part -> {
                String name = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                return !name.startsWith("utm_") && !Set.of("fbclid", "gclid", "mc_cid", "mc_eid", "_ga").contains(name);
            }).reduce((a, b) -> a + "&" + b).orElse(null);
        }
        String host = uri.getHost();
        if (host == null) return uri;
        String path = uri.getRawPath();
        return URI.create(uri.getScheme().toLowerCase(Locale.ROOT) + "://"
                + (uri.getRawUserInfo() == null ? "" : uri.getRawUserInfo() + "@") + host.toLowerCase(Locale.ROOT)
                + (uri.getPort() == -1 || uri.getPort() == 443 ? "" : ":" + uri.getPort())
                + (path == null || path.isEmpty() ? "/" : path) + (query == null ? "" : "?" + query)).normalize();
    }

    private static List<URI> configuredUrls(String configured) {
        if (configured == null || configured.isBlank()) return List.of();
        try { return Arrays.stream(configured.split("[,\\r\\n]+"))
                .map(String::trim).filter(s -> !s.isEmpty()).map(URI::create).distinct().toList(); }
        catch (IllegalArgumentException e) { throw new TechniqueSourceException("穿搭技巧来源配置格式错误"); }
    }

    private static String validDate(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        try { return OffsetDateTime.parse(clean).toInstant().toString(); } catch (Exception ignored) { }
        try { return Instant.parse(clean).toString(); } catch (Exception ignored) { }
        try { return ZonedDateTime.parse(clean, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toString(); } catch (Exception ignored) { }
        try { return LocalDate.parse(clean).toString(); } catch (Exception ignored) { return null; }
    }

    private static String baseHost(String host) { return host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", ""); }
    private static String heading(Element element) { return element.select("article h1,main h1,h1:not(.site-title)").stream().map(Element::text).map(String::trim).filter(s -> !s.isBlank()).findFirst().orElse(null); }
    private static String meta(Element element, String key, String value) { Element found = element.selectFirst("meta[" + key + "=\"" + value + "\"]"); return found == null ? null : first(found.attr("content")); }
    private static String text(Element element, String selector) { Element found = element.selectFirst(selector); return found == null ? null : first(found.text()); }
    private static String tagText(Element element, String tag) { Element found = element.getElementsByTag(tag).first(); return found == null ? null : first(found.text()); }
    private static String jsonText(JsonNode node, String key) { return node != null && node.path(key).isTextual() ? first(node.path(key).asText()) : null; }
    private static String first(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value.trim(); return null; }
    private static String limit(String value, int max) { return value == null ? null : value.substring(0, Math.min(max, value.length())); }
    private static List<URI> concat(List<URI> first, List<URI> second) { List<URI> all = new ArrayList<>(first); all.addAll(second); return all; }
    private static void add(Map<String, Candidate> map, Candidate candidate) { map.putIfAbsent(normalize(candidate.url()).toString(), candidate); }
    private static String fingerprint(String url) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8))).substring(0, 24); } catch (Exception e) { throw new IllegalStateException(e); } }

    @FunctionalInterface interface PageFetcher { Page fetch(URI uri) throws Exception; }
    @FunctionalInterface interface AddressResolver { InetAddress[] resolve(String host) throws Exception; }
    record Page(URI url, int status, String contentType, byte[] body, String location, String robots) {
        Page { robots = robots == null ? "" : robots; }
        Page(URI url, int status, String contentType, byte[] body, String location) {
            this(url, status, contentType, body, location, "");
        }
    }
    private record Candidate(URI url, String title, String author, String platform, String date) { }
}
