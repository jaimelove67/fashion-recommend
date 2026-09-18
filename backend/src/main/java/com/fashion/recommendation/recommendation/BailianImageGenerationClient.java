package com.fashion.recommendation.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fashion.recommendation.storage.ImageStorage;
import com.fashion.recommendation.storage.StoredImageData;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class BailianImageGenerationClient {
    private static final Logger log = LoggerFactory.getLogger(BailianImageGenerationClient.class);
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String FAILED = "FAILED";
    private static final String CANCELED = "CANCELED";
    private static final int MAX_REFERENCE_IMAGES = 3;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final ImageStorage imageStorage;
    private final String endpoint;
    private final String taskEndpoint;
    private final String apiKey;
    private final String model;
    private final boolean enabled;
    private final String prototypeMale;
    private final String prototypeFemale;
    private final String referenceBaseUrl;
    private final Duration taskTimeout;
    private final Duration pollInterval;

    @Autowired
    public BailianImageGenerationClient(
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            ImageStorage imageStorage,
            @Value("${app.bailian.image.endpoint:https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation}") String endpoint,
            @Value("${app.bailian.image.task-endpoint:https://dashscope.aliyuncs.com/api/v1/tasks}") String taskEndpoint,
            @Value("${app.bailian.api-key:}") String apiKey,
            @Value("${app.bailian.image.model:wan2.6-image}") String model,
            @Value("${app.bailian.image.enabled:true}") boolean enabled,
            @Value("${app.bailian.image.prototype-male:classpath:reference_photo/model-male.png}") String prototypeMale,
            @Value("${app.bailian.image.prototype-female:classpath:reference_photo/model-female.png}") String prototypeFemale,
            @Value("${app.bailian.image.reference-base-url:}") String referenceBaseUrl,
            @Value("${app.bailian.image.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.bailian.image.read-timeout:15s}") Duration readTimeout,
            @Value("${app.bailian.image.task-timeout:90s}") Duration taskTimeout,
            @Value("${app.bailian.image.poll-interval:2s}") Duration pollInterval) {
        this(createRestClient(connectTimeout, readTimeout), objectMapper, resourceLoader, imageStorage,
                endpoint, taskEndpoint, apiKey, model, enabled, prototypeMale, prototypeFemale,
                referenceBaseUrl, taskTimeout, pollInterval);
    }

    BailianImageGenerationClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            ImageStorage imageStorage,
            String endpoint,
            String taskEndpoint,
            String apiKey,
            String model,
            boolean enabled,
            String prototypeMale,
            String prototypeFemale,
            String referenceBaseUrl,
            Duration taskTimeout,
            Duration pollInterval) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.imageStorage = imageStorage;
        this.endpoint = endpoint;
        this.taskEndpoint = taskEndpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled;
        this.prototypeMale = prototypeMale;
        this.prototypeFemale = prototypeFemale;
        this.referenceBaseUrl = referenceBaseUrl;
        this.taskTimeout = taskTimeout;
        this.pollInterval = pollInterval;
    }

    public OutfitImageGenerationResult generate(
            String gender,
            String occasion,
            String city,
            Double temperatureC,
            List<WardrobeItem> items) {
        String normalizedGender = normalizeGender(gender);
        if (!enabled) {
            return unavailable("阿里云人物生图未启用");
        }
        if (!StringUtils.hasText(apiKey)) {
            return unavailable("尚未配置 DASHSCOPE_API_KEY");
        }
        if (normalizedGender == null) {
            return unavailable("个人档案中尚未选择模特性别");
        }

        try {
            String prototype = prototypeDataUri(normalizedGender);
            if (!StringUtils.hasText(prototype)) {
                return unavailable("缺少对应性别的模特原型图");
            }
            String responseBody = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.setBearerAuth(apiKey.trim());
                        headers.set("X-DashScope-Async", "enable");
                    })
                    .body(buildRequest(normalizedGender, occasion, city, temperatureC, items, prototype))
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(responseBody);
            String requestId = text(response, "request_id");
            JsonNode output = response.path("output");
            String taskId = text(output, "task_id");
            if (StringUtils.hasText(taskId)) {
                return poll(taskId, requestId);
            }
            String imageUrl = findImageUrl(response);
            return imageUrl == null
                    ? failed(requestId, providerMessage(response, "阿里云没有返回图片结果"))
                    : succeeded(imageUrl, requestId);
        } catch (RestClientResponseException exception) {
            log.warn("Bailian image request failed with status {}", exception.getStatusCode().value());
            return failed(null, providerMessage(exception.getResponseBodyAsString(), "阿里云人物生图请求失败"));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed(null, "阿里云人物生图等待被中断");
        } catch (Exception exception) {
            log.warn("Bailian image generation failed: {}", exception.getMessage());
            return failed(null, "阿里云人物生图暂时失败，请稍后重试");
        }
    }

    ObjectNode buildRequest(
            String gender,
            String occasion,
            String city,
            Double temperatureC,
            List<WardrobeItem> items,
            String prototype) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        ObjectNode input = request.putObject("input");
        ArrayNode messages = input.putArray("messages");
        ObjectNode message = messages.addObject().put("role", "user");
        ArrayNode content = message.putArray("content");
        content.addObject().put("text", buildPrompt(gender, occasion, city, temperatureC, items));
        content.addObject().put("image", prototype);
        referenceDataUris(items).forEach(uri -> content.addObject().put("image", uri));

        ObjectNode parameters = request.putObject("parameters");
        parameters.put("prompt_extend", true);
        parameters.put("watermark", false);
        parameters.put("n", 1);
        parameters.put("enable_interleave", false);
        // Keep the portrait canvas independent of the last garment reference image ratio.
        parameters.put("size", "800*1200");
        return request;
    }

    private OutfitImageGenerationResult poll(String taskId, String requestId) throws Exception {
        long timeoutMillis = Math.max(1_000L, taskTimeout.toMillis());
        long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        while (System.nanoTime() < deadline) {
            String responseBody = restClient.get()
                    .uri(taskUrl(taskId))
                    .headers(headers -> headers.setBearerAuth(apiKey.trim()))
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode output = response.path("output");
            String status = text(output, "task_status");
            String imageUrl = findImageUrl(response);
            if (SUCCEEDED.equals(status) && imageUrl != null) {
                return succeeded(imageUrl, requestId == null ? text(response, "request_id") : requestId);
            }
            if (FAILED.equals(status) || CANCELED.equals(status)) {
                return failed(requestId, providerMessage(response, "阿里云人物生图任务未完成"));
            }
            Thread.sleep(Math.max(100L, pollInterval.toMillis()));
        }
        return failed(requestId, "阿里云人物生图超时，请稍后重试");
    }

    private String prototypeDataUri(String gender) throws IOException {
        String location = "MALE".equals(gender) ? prototypeMale : prototypeFemale;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream input = resource.getInputStream()) {
            return dataUri(input.readAllBytes(), "image/png");
        }
    }

    private List<String> referenceDataUris(List<WardrobeItem> items) {
        List<String> references = new ArrayList<>();
        for (WardrobeItem item : items == null ? List.<WardrobeItem>of() : items) {
            if (references.size() >= MAX_REFERENCE_IMAGES) {
                break;
            }
            String uri = itemReference(item);
            if (StringUtils.hasText(uri)) {
                references.add(uri);
            }
        }
        return references;
    }

    private String itemReference(WardrobeItem item) {
        if (item == null) {
            return null;
        }
        if (StringUtils.hasText(item.imageObjectKey())) {
            try {
                StoredImageData data = imageStorage.read(item.imageObjectKey());
                return dataUri(data.content(), StringUtils.hasText(data.contentType()) ? data.contentType() : "image/png");
            } catch (RuntimeException exception) {
                log.debug("Unable to read wardrobe reference image {}", item.id(), exception);
                return null;
            }
        }
        String imageUrl = item.imageUrl();
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        if (imageUrl.startsWith("data:image/")) {
            return imageUrl;
        }
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }
        if (!StringUtils.hasText(referenceBaseUrl)) {
            return null;
        }
        return joinUrl(referenceBaseUrl, imageUrl);
    }

    private String buildPrompt(
            String gender,
            String occasion,
            String city,
            Double temperatureC,
            List<WardrobeItem> items) {
        String itemDescription = (items == null ? List.<WardrobeItem>of() : items).stream()
                .map(item -> String.format(Locale.ROOT, "%s（%s，%s，%s）",
                        safe(item.name(), "衣橱单品"), safe(item.category(), "衣物"),
                        safe(item.color(), "未标注颜色"), safe(item.style(), "未标注风格")))
                .reduce((left, right) -> left + "；" + right)
                .orElse("当前衣橱已确认单品");
        String temperature = temperatureC == null ? "未提供" : String.format(Locale.ROOT, "%.0f°C", temperatureC);
        return String.format(Locale.ROOT,
                "请以参考图中的人物作为模特原型，保持其性别、面部特征、体态和全身比例；生成一张%s模特的全身穿搭照片。"
                        + "场景为%s的%s，天气温度约%s。模特必须只穿着以下已确认的衣橱单品：%s。"
                        + "不得添加、替换或虚构任何未列出的衣物、鞋履或配饰；保持单品颜色、类别和材质特征。"
                        + "画面为参考截图同类的干净浅灰背景、正面站立、完整显示头部到鞋子、自然棚拍光线，衣物边界清晰，不能出现文字、水印、商品卡片或拼贴布局。",
                "MALE".equals(gender) ? "男" : "女", safe(occasion, "日常出行"), safe(city, "当前城市"), temperature,
                itemDescription);
    }

    private static String safe(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String taskUrl(String taskId) {
        return taskEndpoint.replaceAll("/+$", "") + "/" + taskId;
    }

    private static String dataUri(byte[] content, String contentType) {
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(content);
    }

    private static String joinUrl(String baseUrl, String path) {
        return baseUrl.replaceAll("/+$", "") + "/" + path.replaceFirst("^/+", "");
    }

    private String findImageUrl(JsonNode response) {
        JsonNode choices = response.path("output").path("choices");
        if (choices.isArray()) {
            for (JsonNode choice : choices) {
                for (JsonNode content : choice.path("message").path("content")) {
                    String image = text(content, "image");
                    if (image != null && (image.startsWith("http://") || image.startsWith("https://"))) {
                        return image;
                    }
                }
            }
        }
        for (JsonNode result : response.path("output").path("results")) {
            String image = text(result, "url");
            if (image != null) {
                return image;
            }
        }
        return null;
    }

    private String providerMessage(String responseBody, String fallback) {
        try {
            return providerMessage(objectMapper.readTree(responseBody), fallback);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String providerMessage(JsonNode response, String fallback) {
        String message = text(response, "message");
        if (!StringUtils.hasText(message)) {
            message = text(response.path("output"), "message");
        }
        if (!StringUtils.hasText(message)) {
            return fallback;
        }
        return message.trim().length() > 180 ? message.trim().substring(0, 180) + "…" : message.trim();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value != null && value.isTextual() && StringUtils.hasText(value.textValue())
                ? value.textValue().trim()
                : null;
    }

    private OutfitImageGenerationResult succeeded(String imageUrl, String requestId) {
        return new OutfitImageGenerationResult(SUCCEEDED, imageUrl, model, requestId, null);
    }

    private OutfitImageGenerationResult failed(String requestId, String message) {
        return new OutfitImageGenerationResult(FAILED, null, model, requestId, message);
    }

    private OutfitImageGenerationResult unavailable(String message) {
        return new OutfitImageGenerationResult("UNAVAILABLE", null, model, null, message);
    }

    private static String normalizeGender(String gender) {
        if (!StringUtils.hasText(gender)) {
            return null;
        }
        String normalized = gender.trim().toUpperCase(Locale.ROOT);
        return "MALE".equals(normalized) || "FEMALE".equals(normalized) ? normalized : null;
    }

    private static RestClient createRestClient(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", "fashion-recommendation/0.1")
                .build();
    }
}
