package com.fashion.recommendation.ai;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiModelConfigurationService {
    private final AiModelSettingsRepository settingsRepository;
    private final AiModelDefaults defaults;
    private final AiApiKeyCipher cipher;

    public AiModelConfigurationService(
            AiModelSettingsRepository settingsRepository,
            AiModelDefaults defaults,
            AiApiKeyCipher cipher) {
        this.settingsRepository = settingsRepository;
        this.defaults = defaults;
        this.cipher = cipher;
    }

    public AiModelRuntimeConfig resolve(AiModelCapability capability) {
        AiModelRuntimeConfig environment = defaults.get(capability);
        Optional<AiModelSettingRow> stored = settingsRepository.find(capability);
        if (stored.isEmpty()) {
            String source = environment.credentialConfigured() ? "ENVIRONMENT" : "MISSING";
            return new AiModelRuntimeConfig(
                    capability,
                    environment.provider(),
                    environment.model(),
                    environment.apiKey(),
                    environment.enabled(),
                    environment.endpoint(),
                    environment.taskEndpoint(),
                    source,
                    environment.credentialConfigured(),
                    cipher.isAvailable(),
                    false,
                    null);
        }

        AiModelSettingRow row = stored.get();
        String apiKey = null;
        String credentialSource = "MISSING";
        boolean credentialConfigured = false;
        if (StringUtils.hasText(row.encryptedApiKey())) {
            Optional<String> decrypted = cipher.decrypt(row.encryptedApiKey());
            if (decrypted.isPresent() && StringUtils.hasText(decrypted.get())) {
                apiKey = decrypted.get();
                credentialSource = "DATABASE";
                credentialConfigured = true;
            } else {
                credentialSource = "UNAVAILABLE";
            }
        } else if (row.provider() == environment.provider() && environment.credentialConfigured()) {
            apiKey = environment.apiKey();
            credentialSource = "ENVIRONMENT";
            credentialConfigured = true;
        }

        String endpoint;
        String taskEndpoint;
        if (capability == AiModelCapability.DAILY_IMAGE_GENERATION) {
            endpoint = environment.endpoint();
            taskEndpoint = environment.taskEndpoint();
        } else {
            endpoint = row.provider().chatCompletionsEndpoint();
            taskEndpoint = null;
        }
        return new AiModelRuntimeConfig(
                capability,
                row.provider(),
                row.model(),
                apiKey,
                row.enabled(),
                endpoint,
                taskEndpoint,
                credentialSource,
                credentialConfigured,
                cipher.isAvailable(),
                true,
                row.updatedAt());
    }

    /**
     * Environment switches remain a hard safety boundary even when a database override exists.
     * The persisted enabled flag can turn a permitted capability off, but cannot re-enable an
     * integration deliberately disabled by deployment configuration.
     */
    public boolean isEnvironmentEnabled(AiModelCapability capability) {
        return defaults.get(capability).enabled();
    }

    public boolean isEffectivelyEnabled(AiModelRuntimeConfig runtimeConfig) {
        return runtimeConfig.enabled() && isEnvironmentEnabled(runtimeConfig.capability());
    }
}
