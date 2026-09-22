package com.fashion.recommendation.ai;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiModelDefaults {
    private final Map<AiModelCapability, AiModelRuntimeConfig> defaults = new EnumMap<>(AiModelCapability.class);

    public AiModelDefaults(
            @Value("${app.bailian.endpoint:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
                    String chatEndpoint,
            @Value("${app.bailian.api-key:}") String apiKey,
            @Value("${app.bailian.model:qwen-plus}") String recommendationModel,
            @Value("${app.bailian.enabled:true}") boolean recommendationEnabled,
            @Value("${app.bailian.vision-model:qwen-vl-plus}") String visionModel,
            @Value("${app.bailian.vision-enabled:false}") boolean visionEnabled,
            @Value("${app.bailian.image.endpoint:https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation}")
                    String imageEndpoint,
            @Value("${app.bailian.image.task-endpoint:https://dashscope.aliyuncs.com/api/v1/tasks}")
                    String imageTaskEndpoint,
            @Value("${app.bailian.image.model:wan2.6-image}") String imageModel,
            @Value("${app.bailian.image.enabled:true}") boolean imageEnabled) {
        AiModelProvider chatProvider = AiModelProvider.fromEndpoint(chatEndpoint);
        defaults.put(AiModelCapability.WARDROBE_RECOGNITION,
                environment(AiModelCapability.WARDROBE_RECOGNITION, chatProvider, visionModel,
                        apiKey, visionEnabled, chatEndpoint, null));
        defaults.put(AiModelCapability.OUTFIT_RECOMMENDATION,
                environment(AiModelCapability.OUTFIT_RECOMMENDATION, chatProvider, recommendationModel,
                        apiKey, recommendationEnabled, chatEndpoint, null));
        defaults.put(AiModelCapability.DAILY_IMAGE_GENERATION,
                environment(AiModelCapability.DAILY_IMAGE_GENERATION, AiModelProvider.DASHSCOPE, imageModel,
                        apiKey, imageEnabled, imageEndpoint, imageTaskEndpoint));
    }

    public AiModelRuntimeConfig get(AiModelCapability capability) {
        return defaults.get(capability);
    }

    private static AiModelRuntimeConfig environment(
            AiModelCapability capability,
            AiModelProvider provider,
            String model,
            String apiKey,
            boolean enabled,
            String endpoint,
            String taskEndpoint) {
        boolean configured = apiKey != null && !apiKey.isBlank();
        return new AiModelRuntimeConfig(
                capability,
                provider,
                model,
                apiKey,
                enabled,
                endpoint,
                taskEndpoint,
                configured ? "ENVIRONMENT" : "MISSING",
                configured,
                false,
                false,
                null);
    }
}
