package com.fashion.recommendation.trend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class ConfiguredJsonTrendSourceAdapter implements TrendSourceAdapter {
    private static final Set<String> ITEM_FIELDS = Set.of(
            "id", "platform", "title", "topicTags", "heatScore", "publishedAt", "sourceUrl", "imageUrl");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String platform;
    private final Cache<String, List<TrendItem>> cache;

    @Autowired
    public ConfiguredJsonTrendSourceAdapter(
            ObjectMapper objectMapper,
            @Value("${app.trends.endpoint:}") String endpoint,
            @Value("${app.trends.platform:configured-feed}") String platform,
            @Value("${app.trends.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.trends.read-timeout:5s}") Duration readTimeout,
            @Value("${app.trends.cache-ttl:15m}") Duration cacheTtl) {
        this(createRestClient(connectTimeout, readTimeout), objectMapper, endpoint, platform, cacheTtl);
    }

    ConfiguredJsonTrendSourceAdapter(
            RestClient restClient,
            ObjectMapper objectMapper,
            String endpoint,
            String platform,
            Duration cacheTtl) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.platform = StringUtils.hasText(platform) ? platform.trim() : "configured-feed";
        this.cache = Caffeine.newBuilder().maximumSize(1).expireAfterWrite(cacheTtl).build();
    }

    @Override
    public String platform() {
        return platform;
    }

    @Override
    public List<TrendItem> fetchPublicSnapshots() {
        if (!StringUtils.hasText(endpoint)) {
            return List.of();
        }
        return cache.get("feed", ignored -> fetchAndValidate());
    }

    private List<TrendItem> fetchAndValidate() {
        String response = restClient.get().uri(endpoint).retrieve().body(String.class);
        try {
            JsonNode root = objectMapper.readTree(response);
            if (!root.isObject() || root.size() != 1 || !root.has("items") || !root.get("items").isArray()) {
                throw new TrendSourceException("趋势源响应必须是只包含 items 数组的 JSON 对象");
            }
            Instant fetchedAt = Instant.now();
            List<TrendItem> items = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (JsonNode node : root.get("items")) {
                TrendItem item = parseItem(node, fetchedAt);
                if (!ids.add(item.id())) {
                    throw new TrendSourceException("趋势源包含重复 ID");
                }
                items.add(item);
            }
            if (items.isEmpty() || items.size() > 50) {
                throw new TrendSourceException("趋势源条目数量必须在 1 到 50 之间");
            }
            return List.copyOf(items);
        } catch (TrendSourceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TrendSourceException("趋势源返回了无效 JSON", exception);
        }
    }

    private TrendItem parseItem(JsonNode node, Instant fetchedAt) {
        if (!node.isObject() || !fieldNames(node).equals(ITEM_FIELDS)) {
            throw new TrendSourceException("趋势条目字段不符合约束");
        }
        String id = requiredText(node, "id", 120);
        String itemPlatform = requiredText(node, "platform", 40);
        String title = requiredText(node, "title", 200);
        int heatScore = node.path("heatScore").asInt(-1);
        if (!node.path("heatScore").isIntegralNumber() || heatScore < 0 || heatScore > 100) {
            throw new TrendSourceException("趋势热度必须是 0 到 100 的整数");
        }
        List<String> tags = parseTags(node.path("topicTags"));
        Instant publishedAt = parseInstant(requiredText(node, "publishedAt", 60), "publishedAt");
        String sourceUrl = requiredHttpUrl(node, "sourceUrl");
        String imageUrl = optionalHttpUrl(node, "imageUrl");
        return new TrendItem(id, itemPlatform, title, tags, heatScore, publishedAt, fetchedAt,
                sourceUrl, false, imageUrl);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static List<String> parseTags(JsonNode node) {
        if (!node.isArray() || node.isEmpty() || node.size() > 10) {
            throw new TrendSourceException("趋势标签数量必须在 1 到 10 之间");
        }
        List<String> tags = new ArrayList<>();
        node.forEach(tag -> {
            if (!tag.isTextual() || !StringUtils.hasText(tag.textValue()) || tag.textValue().trim().length() > 40) {
                throw new TrendSourceException("趋势标签格式不合法");
            }
            tags.add(tag.textValue().trim());
        });
        return List.copyOf(tags);
    }

    private static String requiredText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || !StringUtils.hasText(value.textValue())
                || value.textValue().trim().length() > maxLength) {
            throw new TrendSourceException("趋势字段 " + field + " 不合法");
        }
        return value.textValue().trim();
    }

    private static String requiredHttpUrl(JsonNode node, String field) {
        String value = requiredText(node, field, 1000);
        validateHttpUrl(value, field);
        return value;
    }

    private static String optionalHttpUrl(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNull()) {
            return null;
        }
        String url = requiredText(node, field, 1000);
        validateHttpUrl(url, field);
        return url;
    }

    private static void validateHttpUrl(String value, String field) {
        try {
            URI uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new TrendSourceException("趋势字段 " + field + " 必须是 HTTP(S) URL");
        }
    }

    private static Instant parseInstant(String value, String field) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new TrendSourceException("趋势字段 " + field + " 必须是 ISO-8601 时间");
        }
    }

    private static RestClient createRestClient(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory).build();
    }
}
