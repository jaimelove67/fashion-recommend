package com.fashion.recommendation.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fashion.recommendation.ai.AiModelCapability;
import com.fashion.recommendation.ai.AiModelConfigurationService;
import com.fashion.recommendation.ai.AiModelProvider;
import com.fashion.recommendation.ai.AiModelRuntimeConfig;
import com.fashion.recommendation.storage.ImageStorage;
import com.fashion.recommendation.storage.StoredImageData;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
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
    private static final int MIN_IMAGE_DIMENSION = 240;
    private static final int MAX_IMAGE_DIMENSION = 8_000;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

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
    private final AiModelConfigurationService modelConfigurationService;

    @Autowired
    public BailianImageGenerationClient(
            ObjectMapper objectMapper,
            AiModelConfigurationService modelConfigurationService,
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
            @Value("${app.bailian.image.read-timeout:120s}") Duration readTimeout,
            @Value("${app.bailian.image.task-timeout:90s}") Duration taskTimeout,
            @Value("${app.bailian.image.poll-interval:2s}") Duration pollInterval) {
        this(createRestClient(connectTimeout, readTimeout), objectMapper, resourceLoader, imageStorage,
                endpoint, taskEndpoint, apiKey, model, enabled, prototypeMale, prototypeFemale,
                referenceBaseUrl, taskTimeout, pollInterval, modelConfigurationService);
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
        this(restClient, objectMapper, resourceLoader, imageStorage, endpoint, taskEndpoint, apiKey, model,
                enabled, prototypeMale, prototypeFemale, referenceBaseUrl, taskTimeout, pollInterval, null);
    }

    private BailianImageGenerationClient(
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
            Duration pollInterval,
            AiModelConfigurationService modelConfigurationService) {
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
        this.modelConfigurationService = modelConfigurationService;
    }

    public OutfitImageGenerationResult generate(
            String gender,
            String occasion,
            String city,
            Double temperatureC,
            List<WardrobeItem> items) {
        return generate(gender, occasion, city, temperatureC, items, null);
    }

    public OutfitImageGenerationResult generate(
            String gender,
            String occasion,
            String city,
            Double temperatureC,
            List<WardrobeItem> items,
            StoredImageData personalPhoto) {
        AiModelRuntimeConfig config = runtimeConfig();
        String normalizedGender = normalizeGender(gender);
        if (!isEffectivelyEnabled(config)) {
            return unavailable("穿搭效果图生成功能尚未启用，请联系管理员。", config.model());
        }
        if (!StringUtils.hasText(config.apiKey())) {
            return unavailable("穿搭效果图生成服务尚未配置，请联系管理员。", config.model());
        }
        if (normalizedGender == null) {
            return unavailable("请在个人形象档案中设置模特性别。", config.model());
        }

        try {
            String prototype = personalPhoto == null ? prototypeDataUri(normalizedGender) : referenceDataUri(personalPhoto.content());
            if (!StringUtils.hasText(prototype)) {
                return unavailable("当前模特的效果图生成配置不完整，请联系管理员。", config.model());
            }
            String responseBody = restClient.post()
                    .uri(config.endpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    // Wan 2.6 image generation returns the image synchronously. Forcing
                    // DashScope async mode can reject an otherwise valid model/API key.
                    .headers(headers -> headers.setBearerAuth(config.apiKey().trim()))
                    .body(buildRequest(normalizedGender, occasion, city, temperatureC, items, prototype, config.model()))
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(responseBody);
            String requestId = text(response, "request_id");
            JsonNode output = response.path("output");
            String taskId = text(output, "task_id");
            if (StringUtils.hasText(taskId)) {
                return poll(taskId, requestId, config);
            }
            String imageUrl = findImageUrl(response);
            return imageUrl == null
                    ? failed(requestId, providerMessage(response, "未获得有效的穿搭效果图，请重新生成。"), config.model())
                    : succeeded(imageUrl, requestId, config.model());
        } catch (RestClientResponseException exception) {
            log.warn("Bailian image request failed with status {}", exception.getStatusCode().value());
            return failed(null, providerMessage(exception.getResponseBodyAsString(), "穿搭效果图生成失败，请稍后重试。"), config.model());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed(null, "穿搭效果图生成已中断，请重新生成。", config.model());
        } catch (Exception exception) {
            log.warn("Bailian image generation failed: {}", exception.getMessage());
            return failed(null, "穿搭效果图生成失败，请稍后重试。", config.model());
        }
    }

    ObjectNode buildRequest(
            String gender,
            String occasion,
            String city,
            Double temperatureC,
            List<WardrobeItem> items,
            String prototype) {
        return buildRequest(gender, occasion, city, temperatureC, items, prototype, model);
    }

    private ObjectNode buildRequest(
            String gender,
            String occasion,
            String city,
            Double temperatureC,
            List<WardrobeItem> items,
            String prototype,
            String selectedModel) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", selectedModel);
        ObjectNode input = request.putObject("input");
        ArrayNode messages = input.putArray("messages");
        ObjectNode message = messages.addObject().put("role", "user");
        ArrayNode content = message.putArray("content");
        content.addObject().put("text", buildPrompt(gender, occasion, city, temperatureC, items));
        content.addObject().put("image", prototype);
        referenceDataUris(items).forEach(uri -> content.addObject().put("image", uri));

        ObjectNode parameters = request.putObject("parameters");
        // Keep the explicit background constraint instead of expanding it into a scene.
        parameters.put("prompt_extend", false);
        parameters.put("watermark", false);
        parameters.put("n", 1);
        parameters.put("enable_interleave", false);
        // Keep the portrait canvas independent of the last garment reference image ratio.
        parameters.put("size", "800*1200");
        return request;
    }

    private OutfitImageGenerationResult poll(
            String taskId, String requestId, AiModelRuntimeConfig config) throws Exception {
        long timeoutMillis = Math.max(1_000L, taskTimeout.toMillis());
        long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        while (System.nanoTime() < deadline) {
            String responseBody = restClient.get()
                    .uri(taskUrl(taskId, config.taskEndpoint()))
                    .headers(headers -> headers.setBearerAuth(config.apiKey().trim()))
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode output = response.path("output");
            String status = text(output, "task_status");
            String imageUrl = findImageUrl(response);
            if (SUCCEEDED.equals(status) && imageUrl != null) {
                return succeeded(imageUrl, requestId == null ? text(response, "request_id") : requestId, config.model());
            }
            if (FAILED.equals(status) || CANCELED.equals(status)) {
                return failed(requestId, providerMessage(response, "穿搭效果图生成未完成，请稍后重试。"), config.model());
            }
            Thread.sleep(Math.max(100L, pollInterval.toMillis()));
        }
        return failed(requestId, "穿搭效果图生成超时，请稍后重试。", config.model());
    }

    private String prototypeDataUri(String gender) throws IOException {
        String location = "MALE".equals(gender) ? prototypeMale : prototypeFemale;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream input = resource.getInputStream()) {
            return referenceDataUri(input.readAllBytes());
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
                return referenceDataUri(data.content());
            } catch (IOException | RuntimeException exception) {
                log.debug("Unable to read wardrobe reference image {}", item.id(), exception);
                return null;
            }
        }
        String imageUrl = item.imageUrl();
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        if (imageUrl.startsWith("data:image/")) {
            try {
                int separator = imageUrl.indexOf(',');
                if (separator < 0 || !imageUrl.substring(0, separator).endsWith(";base64")) {
                    return null;
                }
                return referenceDataUri(Base64.getDecoder().decode(imageUrl.substring(separator + 1)));
            } catch (IOException | IllegalArgumentException exception) {
                log.debug("Unable to decode wardrobe reference image {}", item.id());
                return null;
            }
        }
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }
        if (!StringUtils.hasText(referenceBaseUrl)) {
            return null;
        }
        return joinUrl(referenceBaseUrl, imageUrl);
    }

    // Wan 2.6 rejects alpha channels and images with either dimension below 240.
    // Normalize only the provider input; keep the user's stored photo unchanged.
    private static String referenceDataUri(byte[] content) throws IOException {
        if (content == null || content.length == 0 || content.length > MAX_IMAGE_BYTES) {
            throw new IOException("Reference image is empty or exceeds the size limit");
        }
        BufferedImage source;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Reference image cannot be decoded");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // Check the header before decoding to avoid allocating an oversized image.
                if (width < 1 || height < 1 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                    throw new IOException("Reference image dimensions exceed the limit");
                }
                source = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        double scale = Math.max(1.0, (double) MIN_IMAGE_DIMENSION / Math.min(source.getWidth(), source.getHeight()));
        int width = (int) Math.ceil(source.getWidth() * scale);
        int height = (int) Math.ceil(source.getHeight() * scale);
        if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
            throw new IOException("Reference image aspect ratio prevents safe resizing");
        }
        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(rgb, "jpeg", output) || output.size() > MAX_IMAGE_BYTES) {
            throw new IOException("Reference image cannot be encoded within the size limit");
        }
        return dataUri(output.toByteArray(), "image/jpeg");
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
                        + "第一张图片是人物参考，之后的图片仅是衣物参考。面部与发型以第一张人物参考为准，不得用衣物图片中的人物替换模特。"
                        + "保持自然的头身比例和统一的写实摄影风格；人物参考未显示的身体部位应自然补全，不要将头像放大或拼接到另一具身体上。"
                        + "穿搭适用场合为%s，所在城市为%s，天气温度约%s；这些信息只用于穿搭表达，不用于生成环境。模特必须只穿着以下已确认的衣橱单品：%s。"
                        + "不得添加、替换或虚构任何未列出的衣物、鞋履或配饰；保持单品颜色、类别和材质特征。"
                        + "模特正面站立，完整显示头部到鞋子，人物使用均匀柔和的补光，衣物边界清晰，不能出现文字、水印、商品卡片或拼贴布局。"
                        + "背景必须为纯色浅灰背景（#F2F2F2），整张背景使用均匀的单一浅色。禁止背景渐变、纹理、图案、明显阴影、地平线、室内陈设、街景、风景或其他物件。"
                        + "此背景要求优先于参考图和穿搭场合中的场景信息，不得复制参考图的原有背景或环境。",
                "MALE".equals(gender) ? "男" : "女", safe(occasion, "日常出行"), safe(city, "当前城市"), temperature,
                itemDescription);
    }

    private static String safe(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String taskUrl(String taskId, String configuredTaskEndpoint) {
        return configuredTaskEndpoint.replaceAll("/+$", "") + "/" + taskId;
    }

    private AiModelRuntimeConfig runtimeConfig() {
        if (modelConfigurationService != null) {
            return modelConfigurationService.resolve(AiModelCapability.DAILY_IMAGE_GENERATION);
        }
        return new AiModelRuntimeConfig(
                AiModelCapability.DAILY_IMAGE_GENERATION,
                AiModelProvider.DASHSCOPE,
                model,
                apiKey,
                enabled,
                endpoint,
                taskEndpoint,
                StringUtils.hasText(apiKey) ? "ENVIRONMENT" : "MISSING",
                StringUtils.hasText(apiKey),
                false,
                false,
                null);
    }

    private boolean isEffectivelyEnabled(AiModelRuntimeConfig config) {
        return modelConfigurationService == null
                ? config.enabled()
                : modelConfigurationService.isEffectivelyEnabled(config);
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

    private static OutfitImageGenerationResult succeeded(String imageUrl, String requestId, String selectedModel) {
        return new OutfitImageGenerationResult(SUCCEEDED, imageUrl, selectedModel, requestId, null);
    }

    private static OutfitImageGenerationResult failed(String requestId, String message, String selectedModel) {
        return new OutfitImageGenerationResult(FAILED, null, selectedModel, requestId, message);
    }

    private static OutfitImageGenerationResult unavailable(String message, String selectedModel) {
        return new OutfitImageGenerationResult("UNAVAILABLE", null, selectedModel, null, message);
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
