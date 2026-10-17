package com.fashion.recommendation.technique;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TechniqueSourceCollectorTest {
    private static final URI FEED = URI.create("https://style.example.com/feed");
    private static final URI ARTICLE = URI.create("https://style.example.com/how-to-wear-trousers");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC);
    private final Map<URI, TechniqueSourceCollector.Page> pages = new LinkedHashMap<>();
    private final List<URI> requested = new ArrayList<>();

    @Test
    void discoversRssButReadsActualArticleAndKeepsOnlyBoundedEvidence() {
        page(FEED, "application/rss+xml", """
                <rss xmlns:dc="http://purl.org/dc/elements/1.1/"><channel><title>Style Journal</title>
                <item><title>How to wear trousers</title><link>https://style.example.com/how-to-wear-trousers?utm_source=rss</link>
                <dc:creator>Feed Writer</dc:creator><pubDate>Thu, 15 Oct 2026 08:00:00 +0000</pubDate>
                <description>FEED SUMMARY MUST NOT BE EVIDENCE</description></item></channel></rss>
                """);
        page(ARTICLE, "text/html", article("""
                <meta name="author" content="Actual Writer"><meta property="og:site_name" content="Publisher">
                <link rel="canonical" href="/how-to-wear-trousers?utm_campaign=test">
                """));

        List<TechniqueArticle> result = collector(List.of(FEED), List.of()).collect();

        assertEquals(List.of(FEED, ARTICLE), requested);
        assertEquals(1, result.size());
        TechniqueArticle item = result.get(0);
        assertEquals(ARTICLE.toString(), item.url());
        assertEquals("Actual Writer", item.author());
        assertEquals("Publisher", item.platform());
        assertEquals("2026-10-15T08:00:00Z", item.publishedAt());
        assertEquals(CLOCK.instant(), item.fetchedAt());
        assertTrue(item.text().contains("Keep the same trousers"));
        assertFalse(item.text().contains("FEED SUMMARY"));
        assertFalse(item.text().contains("COMMENT CONTENT"));
        assertEquals(24, item.id().length());
    }

    @Test
    void supportsAtomAndUnknownDatesAndAuthorsStayUnknown() {
        page(FEED, "application/atom+xml", """
                <feed xmlns="http://www.w3.org/2005/Atom"><title>Style Journal</title>
                <entry><title>How to wear trousers</title><link rel="alternate" href="/how-to-wear-trousers"/>
                <updated>2026-10-15T08:00:00Z</updated></entry></feed>
                """);
        page(ARTICLE, "text/html", article(""));

        TechniqueArticle result = collector(List.of(FEED), List.of()).collect().get(0);

        assertNull(result.author());
        assertNull(result.publishedAt()); // Modification time is not a publication date.
        assertEquals("Style Journal", result.platform());
    }

    @Test
    void ignoresBrokenFeedAndStillReadsAConfiguredArticleWithJsonLdMetadata() {
        page(ARTICLE, "text/html", article("""
                <script type="application/ld+json">{"@graph":[{"@type":"BlogPosting",
                "author":{"@type":"Person","name":"Jane Writer"},"datePublished":"2020-01-02",
                "publisher":{"name":"Original Journal"}}]}</script>
                """));

        TechniqueArticle result = collector(List.of(FEED), List.of(ARTICLE)).collect().get(0);

        assertEquals("Jane Writer", result.author());
        assertEquals("2020-01-02", result.publishedAt());
        assertEquals("Original Journal", result.platform());
    }

    @Test
    void rejectsFeedSummaryAndBlockedOrMinimalArticlePages() {
        for (String bad : List.of("<article><p>How to wear trousers, a very short summary.</p></article>",
                article("<meta name=robots content=noai>"), article("<meta name=robots content=noarchive>"),
                article("<div class=paywall>Subscribe</div>"), article("").replace("Keep the same trousers", "Subscribe to continue reading"))) {
            pages.clear();
            page(ARTICLE, "text/html", bad);
            assertThrows(TechniqueSourceException.class, () -> collector(List.of(), List.of(ARTICLE)).collect());
        }
    }

    @Test
    void skipsShoppingListsAndOffDomainDiscoveriesWithoutFetchingThem() {
        page(FEED, "application/rss+xml", """
                <rss><channel><item><title>Shopping guide to jackets</title><link>https://style.example.com/shop</link></item>
                <item><title>Ask me anything about new knitwear</title><link>https://style.example.com/ama</link></item>
                <item><title>How product design works</title><link>https://style.example.com/product</link></item>
                <item><title>How to wear trousers</title><link>https://outside.example.net/trousers</link></item>
                <item><title>How to wear trousers</title><link>https://style.example.com/how-to-wear-trousers</link></item>
                </channel></rss>
                """);
        page(ARTICLE, "text/html", article(""));
        assertEquals(1, collector(List.of(FEED), List.of()).collect().size());
        assertEquals(List.of(FEED, ARTICLE), requested);
    }

    @Test
    void refusesPrivateDnsAnswersAndIpOrCredentialTargetsBeforeAnyHttpRequest() throws Exception {
        for (String address : List.of("127.0.0.1", "10.0.0.1", "169.254.169.254", "100.100.100.200", "::1", "fc00::1")) {
            var collector = new TechniqueSourceCollector(new ObjectMapper(), List.of(), List.of(ARTICLE), this::fetch,
                    host -> new InetAddress[]{InetAddress.getByName(address)}, CLOCK);
            assertThrows(TechniqueSourceException.class, collector::collect);
        }
        assertTrue(requested.isEmpty());
        for (String address : List.of("http://style.example.com/a", "https://127.0.0.1/a", "https://user:password@style.example.com/a",
                "https://style.example.com:8443/a", "https://localhost/a", "https://service.internal/a")) {
            assertThrows(TechniqueSourceException.class, () -> collector(List.of(), List.of(URI.create(address))));
        }
    }

    @Test
    void revalidatesRedirectsAndRejectsOutsideHostOrPublicToPrivateDns() throws Exception {
        pages.put(ARTICLE, new TechniqueSourceCollector.Page(ARTICLE, 302, "text/html", new byte[0], "https://evil.example.net/private"));
        assertThrows(TechniqueSourceException.class, () -> collector(List.of(), List.of(ARTICLE)).collect());
        assertEquals(List.of(ARTICLE), requested);
        requested.clear();
        pages.put(ARTICLE, new TechniqueSourceCollector.Page(ARTICLE, 302, "text/html", new byte[0], "/next"));
        int[] lookups = {0};
        var collector = new TechniqueSourceCollector(new ObjectMapper(), List.of(), List.of(ARTICLE), this::fetch,
                host -> new InetAddress[]{InetAddress.getByName(lookups[0]++ == 0 ? "93.184.215.14" : "10.0.0.1")}, CLOCK);
        assertThrows(TechniqueSourceException.class, collector::collect);
        assertEquals(List.of(ARTICLE), requested);
    }

    @Test
    void followsPublicSamePublisherRedirectAndHandlesHtmlDiscovery() {
        page(FEED, "text/html", "<main><a href='/old'>How to wear trousers with different shoe volume</a></main>");
        URI old = URI.create("https://style.example.com/old");
        pages.put(old, new TechniqueSourceCollector.Page(old, 301, "text/html", new byte[0], ARTICLE.toString()));
        page(ARTICLE, "text/html", article(""));
        assertEquals(ARTICLE.toString(), collector(List.of(FEED), List.of()).collect().get(0).url());
        assertEquals(List.of(FEED, old, ARTICLE), requested);
    }

    @Test
    void rejectsOversizedResponsesAndDoesNotPublishTruncatedLongArticles() {
        pages.put(ARTICLE, new TechniqueSourceCollector.Page(ARTICLE, 200, "text/html", new byte[TechniqueSourceCollector.MAX_BODY_BYTES + 1], null));
        assertThrows(TechniqueSourceException.class, () -> collector(List.of(), List.of(ARTICLE)).collect());
        page(ARTICLE, "text/html", article("").replace("</article>", "<p>" + "wear trousers with contrast ".repeat(2000) + "</p></article>"));
        assertThrows(TechniqueSourceException.class, () -> collector(List.of(), List.of(ARTICLE)).collect());
    }

    @Test
    void removesOnlyKnownTrackingParametersAndKeepsArticleIdentityParameters() {
        assertEquals("https://style.example.com/read?id=42&lang=zh&encoded=a%2Fb",
                TechniqueSourceCollector.normalize(URI.create("https://style.example.com/read?id=42&utm_source=rss&lang=zh&fbclid=a&encoded=a%2Fb#comments")).toString());
    }

    @Test
    void retainsShortQualifiersAndLegacyDivParagraphsWithoutDuplicatingTheirParents() {
        String html = article("").replace("</article>", "<div><div>Not in summer.</div><div>仅限室内。</div></div></article>")
                .replace("<p>", "<div>").replace("</p>", "</div>");
        page(ARTICLE, "text/html", html);
        String text = collector(List.of(), List.of(ARTICLE)).collect().get(0).text();
        assertTrue(text.contains("Not in summer."));
        assertTrue(text.contains("仅限室内。"));
        assertEquals(1, text.split("Not in summer\\.", -1).length - 1);
    }

    @Test
    void ignoresSiteLogoHeadingWhenPublisherMetadataOnlyNamesTheSite() {
        page(ARTICLE, "text/html", article("<meta property='og:title' content='Style Journal'>")
                .replace("<body>", "<body><h1 class=site-title>Style Journal</h1>"));
        assertEquals("How to wear trousers and balance an outfit", collector(List.of(), List.of(ARTICLE)).collect().get(0).title());
    }

    @Test
    void stopsAfterSixDistinctReadableArticles() {
        List<URI> urls = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            URI uri = URI.create("https://style.example.com/article/" + i);
            urls.add(uri);
            page(uri, "text/html", article(""));
        }
        assertEquals(6, collector(List.of(), urls).collect().size());
        assertEquals(6, requested.size());
    }

    @Test
    void rotatesCandidatesByShanghaiIsoWeekAndKeepsPublisherInterleaving() {
        URI secondFeed = URI.create("https://other.example.net/feed");
        for (URI feed : List.of(FEED, secondFeed)) {
            StringBuilder rss = new StringBuilder("<rss><channel>");
            for (int i = 0; i < 10; i++) {
                URI uri = feed.resolve("/tutorial-" + i);
                rss.append("<item><title>How to wear trousers ").append(i).append("</title><link>")
                        .append(uri).append("</link></item>");
                page(uri, "text/html", article(""));
            }
            page(feed, "application/rss+xml", rss.append("</channel></rss>").toString());
        }
        List<String> thisWeek = collectorAt(List.of(FEED, secondFeed), CLOCK).collect().stream().map(TechniqueArticle::url).toList();
        Clock sameShanghaiWeek = Clock.fixed(Instant.parse("2026-10-18T15:59:59Z"), ZoneOffset.UTC);
        assertEquals(thisWeek, collectorAt(List.of(FEED, secondFeed), sameShanghaiWeek).collect().stream().map(TechniqueArticle::url).toList());
        Clock nextShanghaiWeek = Clock.fixed(Instant.parse("2026-10-18T16:00:00Z"), ZoneOffset.UTC);
        List<String> nextWeek = collectorAt(List.of(FEED, secondFeed), nextShanghaiWeek).collect().stream().map(TechniqueArticle::url).toList();
        assertNotEquals(thisWeek, nextWeek);
        for (List<String> selected : List.of(thisWeek, nextWeek)) {
            assertEquals(6, selected.size());
            for (int i = 0; i < selected.size(); i++) {
                assertEquals(i % 2 == 0 ? "style.example.com" : "other.example.net", URI.create(selected.get(i)).getHost());
            }
        }
    }

    private TechniqueSourceCollector collector(List<URI> feeds, List<URI> articles) {
        return new TechniqueSourceCollector(new ObjectMapper(), feeds, articles, this::fetch,
                host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{93, (byte)184, (byte)215, 14})}, CLOCK);
    }

    private TechniqueSourceCollector collectorAt(List<URI> feeds, Clock clock) {
        return new TechniqueSourceCollector(new ObjectMapper(), feeds, List.of(), this::fetch,
                host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{93, (byte)184, (byte)215, 14})}, clock);
    }

    private TechniqueSourceCollector.Page fetch(URI uri) {
        requested.add(uri);
        if (!pages.containsKey(uri)) throw new TechniqueSourceException("Unavailable fixture");
        return pages.get(uri);
    }

    private void page(URI uri, String type, String body) {
        pages.put(uri, new TechniqueSourceCollector.Page(uri, 200, type, body.getBytes(StandardCharsets.UTF_8), null));
    }

    private static String article(String metadata) {
        String paragraph = "Keep the same trousers and compare two shoes before changing the hem. "
                + "The volume of a shoe can change how the fabric falls at the ankle. "
                + "Choose the proportion you prefer, keeping comfort and freedom of movement. "
                + "Colour contrast and fabric texture also matter when combining a formal jacket and casual trousers.";
        return "<html><head>" + metadata + "</head><body><h1>How to wear trousers and balance an outfit</h1>"
                + "<article class=entry-content>" + ("<p>" + paragraph + "</p>").repeat(6)
                + "<section id=comments><p>COMMENT CONTENT</p></section></article></body></html>";
    }
}
