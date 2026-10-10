package com.fashion.recommendation.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.recognition.BailianGarmentRecognitionService;
import com.fashion.recommendation.recommendation.*;
import com.fashion.recommendation.storage.ImageStorage;
import com.fashion.recommendation.storage.StoredImageData;
import com.fashion.recommendation.style.*;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import jakarta.validation.Validation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in paid provider checks. Uses synthetic fixtures, never user accounts or stored photos. */
@EnabledIfEnvironmentVariable(named = "MODEL_LIVE_SMOKE", matches = "true")
class AiModelLiveSmokeTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final String endpoint = env("BAILIAN_ENDPOINT", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");
    private final String key = env("DASHSCOPE_API_KEY", "");
    private final String vision = env("BAILIAN_VISION_MODEL", "qwen-vl-plus");
    private final String recommendation = env("BAILIAN_MODEL", "qwen-plus");
    private final String imageModel = env("BAILIAN_IMAGE_MODEL", "wan2.6-image");
    private final String imageEndpoint = env("BAILIAN_IMAGE_ENDPOINT", "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation");
    private final String taskEndpoint = env("BAILIAN_IMAGE_TASK_ENDPOINT", "https://dashscope.aliyuncs.com/api/v1/tasks");
    private final AiModelConfigurationService configuration = configuration();

    @Test
    void personalAnalysisReturnsValidatedObservation() throws Exception {
        try (var validator = Validation.buildDefaultValidatorFactory()) {
            var client = new BailianPersonalStyleAnalysisClient(mapper, validator.getValidator(), configuration,
                    Duration.ofSeconds(5), Duration.ofSeconds(60));
            var result = client.analyze(profile(), new StoredImageData(photo(), "image/png"));
            assertNotNull(result.analysis());
            assertFalse(result.modelName().isBlank());
        }
    }

    @Test
    void clothingRecognitionReturnsStructuredFields() throws Exception {
        var client = new BailianGarmentRecognitionService(mapper, configuration, endpoint, key, vision, true,
                Duration.ofSeconds(5), Duration.ofSeconds(60));
        var result = client.recognize(new MockMultipartFile("image", "synthetic.png", "image/png", photo()));
        assertTrue(result.isPresent(), "Vision provider must return parseable clothing fields");
    }

    @Test
    void recommendationReturnsRealProviderResult() {
        var client = new BailianRecommendationClient(mapper, configuration, endpoint, key, recommendation, true,
                Duration.ofSeconds(5), Duration.ofSeconds(60));
        var result = client.recommend(new LlmRecommendationContext("通勤", "简约", wardrobe(), null, profile(), Map.of()));
        assertTrue(result.isPresent());
        assertTrue(result.get().itemIds().size() >= 2);
    }

    @Test
    void outfitGenerationReturnsImage() {
        var client = new BailianImageGenerationClient(mapper, configuration, new DefaultResourceLoader(), mock(ImageStorage.class),
                imageEndpoint, taskEndpoint, key, imageModel, true,
                "classpath:reference_photo/model-male.png", "classpath:reference_photo/model-female.png", "",
                Duration.ofSeconds(5), Duration.ofSeconds(120), Duration.ofSeconds(90), Duration.ofSeconds(2));
        var result = client.generate("MALE", "通勤", "上海", 22.0, wardrobe());
        assertTrue(result.succeeded(), result.message());
    }

    private AiModelConfigurationService configuration() {
        assertFalse(key.isBlank(), "A provider key must be supplied through the environment");
        var repository = mock(AiModelSettingsRepository.class);
        when(repository.find(any())).thenReturn(Optional.empty());
        var defaults = new AiModelDefaults(endpoint, key, recommendation,
                Boolean.parseBoolean(env("BAILIAN_ENABLED", "true")), vision,
                Boolean.parseBoolean(env("BAILIAN_VISION_ENABLED", "false")), imageEndpoint, taskEndpoint, imageModel,
                Boolean.parseBoolean(env("BAILIAN_IMAGE_ENABLED", "true")));
        return new AiModelConfigurationService(repository, defaults, new AiApiKeyCipher(""));
    }

    private static byte[] photo() throws Exception {
        return Files.readAllBytes(Path.of(System.getenv("MODEL_SMOKE_PHOTO")));
    }

    private static StyleProfile profile() {
        return new StyleProfile("合成测试", "MALE", List.of("通勤"), List.of("米白"), List.of("通勤"),
                List.of(), List.of(), List.of(), List.of(), "", "", Instant.now(), false);
    }

    private static List<WardrobeItem> wardrobe() {
        return List.of(new WardrobeItem(1L, "白色衬衫", "上装", "白", "通勤", null, Instant.now()),
                new WardrobeItem(2L, "黑色长裤", "下装", "黑", "通勤", null, Instant.now()),
                new WardrobeItem(3L, "黑色鞋", "鞋履", "黑", "通勤", null, Instant.now()));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
