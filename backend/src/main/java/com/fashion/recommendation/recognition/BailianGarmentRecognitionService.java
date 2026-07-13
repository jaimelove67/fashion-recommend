package com.fashion.recommendation.recognition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Base64;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BailianGarmentRecognitionService implements GarmentRecognitionService {
    private static final String PROMPT = """
            识别这张图片中的主要衣物，只返回 JSON，不要 Markdown 或额外文字。
            JSON 只能包含 name、category、color、style 四个字符串字段；无法确定的字段返回空字符串。
            category 只能从 上装、下装、鞋履、外套、配饰 中选择。
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    @Autowired
    public BailianGarmentRecognitionService(
            ObjectMapper objectMapper,
            @Value("${app.bailian.endpoint:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
                    String endpoint,
            @Value("${app.bailian.api-key:}") String apiKey,
            @Value("${app.bailian.vision-model:qwen-vl-plus}") String model,
            @Value("${app.bailian.vision-enabled:false}") boolean enabled) {
        this(RestClient.builder().build(), objectMapper, endpoint, apiKey, model, enabled);
    }

    BailianGarmentRecognitionService(
            RestClient restClient,
            ObjectMapper objectMapper,
            String endpoint,
            String apiKey,
            String model,
            boolean enabled) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled;
    }

    @Override
    public Optional<GarmentRecognitionResult> recognize(MultipartFile image) {
        if (!enabled || !StringUtils.hasText(apiKey)) {
            return Optional.empty();
        }
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("model", model);
            request.put("temperature", 0.1);
            request.put("max_tokens", 200);
            ArrayNode messages = request.putArray("messages");
            messages.addObject().put("role", "system").put("content", PROMPT);
            ArrayNode content = messages.addObject().putArray("content");
            content.addObject().put("type", "text").put("text", PROMPT);
            ObjectNode imagePart = content.addObject();
            imagePart.put("type", "image_url");
            imagePart.putObject("image_url").put("url", "data:"
                    + image.getContentType() + ";base64," + Base64.getEncoder().encodeToString(image.getBytes()));
            request.putObject("response_format").put("type", "json_object");

            String response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey.trim()))
                    .body(request)
                    .retrieve()
                    .body(String.class);
            JsonNode contentNode = objectMapper.readTree(response).path("choices").path(0)
                    .path("message").path("content");
            if (!contentNode.isTextual()) {
                return Optional.empty();
            }
            JsonNode result = objectMapper.readTree(contentNode.textValue());
            return Optional.of(new GarmentRecognitionResult(
                    text(result, "name"), text(result, "category"), text(result, "color"), text(result, "style")));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText().trim() : "";
    }
}
