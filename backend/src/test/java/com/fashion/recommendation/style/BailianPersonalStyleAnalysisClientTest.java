package com.fashion.recommendation.style;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.ai.*;
import com.fashion.recommendation.storage.StoredImageData;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BailianPersonalStyleAnalysisClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String ANALYSIS = """
            {"faceShape":"椭圆偏长","facialLine":"清晰","visualContrast":"中等","hairFeatures":"短发","bodyProportions":"",
             "fitSuggestions":["圆领","直筒"],"styleTags":["通勤"],"tryStyleTags":[],"colorSuggestions":["米白"],
             "itemSuggestions":["圆领上衣"],"reasonSummary":"按可见特征参考领口与配色。"}
            """;

    @Test
    void sendsPhotoMeasurementsAndPreferencesToConfiguredVisionProvider() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> captured = new AtomicReference<>();
        server.createContext("/chat", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = mapper.createObjectNode().put("model", "actual-vision-model");
            response.putArray("choices").addObject().putObject("message").put("content", ANALYSIS);
            byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var config = configuration("http://127.0.0.1:" + server.getAddress().getPort() + "/chat", true, "test-key");
            var client = new BailianPersonalStyleAnalysisClient(mapper, factory.getValidator(), config, RestClient.create());
            PersonalStyleAnalysisResult result = client.analyze(profile(), new StoredImageData(new byte[] {1, 2, 3}, "image/png"));
            assertEquals("actual-vision-model", result.modelName());
            assertEquals("椭圆偏长", result.analysis().faceShape());
            assertEquals("", result.analysis().bodyProportions());
            var request = mapper.readTree(captured.get());
            assertEquals("configured-vision", request.path("model").asText());
            var user = request.path("messages").get(1).path("content");
            var input = mapper.readTree(user.get(0).path("text").asText());
            assertEquals(175, input.path("heightCm").asDouble());
            assertEquals(65, input.path("weightKg").asDouble());
            assertEquals("通勤", input.path("stylePreferences").get(0).asText());
            assertEquals("data:image/png;base64,AQID", user.get(1).path("image_url").path("url").asText());
            assertFalse(captured.get().contains("private-key"));
            assertFalse(captured.get().contains("姓名不应发送"));
        } finally { server.stop(0); }
    }

    @Test
    void disabledOrMissingCredentialNeverFallsBackToInventedAnalysis() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var photo = new StoredImageData(new byte[] {1}, "image/png");
            for (var config : List.of(configuration("http://127.0.0.1:1/chat", false, "test-key"),
                    configuration("http://127.0.0.1:1/chat", true, ""))) {
                var client = new BailianPersonalStyleAnalysisClient(mapper, factory.getValidator(), config, RestClient.create());
                var failure = assertThrows(ResponseStatusException.class, () -> client.analyze(profile(), photo));
                assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
            }
        }
    }

    @Test
    void rejectsMalformedExtraEmptyAndOversizedModelResults() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var client = new BailianPersonalStyleAnalysisClient(mapper, factory.getValidator(),
                    configuration("http://127.0.0.1:1/chat", true, "test-key"), RestClient.create());
            assertEquals("圆领", client.parseAnalysis(ANALYSIS).fitSuggestions().get(0));
            var extra = mapper.readTree(ANALYSIS).deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) extra).put("extra", "do not accept");
            assertThrows(ResponseStatusException.class, () -> client.parseAnalysis(extra.toString()));
            var empty = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(ANALYSIS);
            for (String field : List.of("faceShape", "facialLine", "visualContrast", "hairFeatures", "bodyProportions")) empty.put(field, "");
            assertThrows(ResponseStatusException.class, () -> client.parseAnalysis(empty.toString()));
            var longText = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(ANALYSIS);
            longText.put("faceShape", "长".repeat(121));
            assertThrows(ResponseStatusException.class, () -> client.parseAnalysis(longText.toString()));
            String fence = String.valueOf((char) 96).repeat(3);
            assertThrows(Exception.class, () -> client.parseAnalysis(fence + "json\n" + ANALYSIS + "\n" + fence));
            assertThrows(Exception.class, () -> client.parseAnalysis(ANALYSIS + "{}"));
        }
    }

    private static AiModelConfigurationService configuration(String endpoint, boolean enabled, String key) {
        var configuration = mock(AiModelConfigurationService.class);
        var runtime = new AiModelRuntimeConfig(AiModelCapability.WARDROBE_RECOGNITION, AiModelProvider.DASHSCOPE,
                "configured-vision", key, enabled, endpoint, null, "ENVIRONMENT", !key.isBlank(), false, false, null);
        when(configuration.resolve(AiModelCapability.WARDROBE_RECOGNITION)).thenReturn(runtime);
        when(configuration.isEffectivelyEnabled(runtime)).thenReturn(enabled);
        return configuration;
    }

    private static StyleProfile profile() {
        return new StyleProfile("姓名不应发送", "MALE", List.of("通勤"), List.of("米白"), List.of("通勤"), List.of(),
                List.of(), List.of(), List.of(), "", "rules", Instant.now(), false,
                175.0, 65.0, "/api/v1/me/style-profile/photo", "private-key", null, null, null, null, 0);
    }
}
