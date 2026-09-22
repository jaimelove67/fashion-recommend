package com.fashion.recommendation.admin;

import com.fashion.recommendation.ai.AiApiKeyCipher;
import com.fashion.recommendation.ai.AiModelConfigurationService;
import com.fashion.recommendation.ai.AiModelCapability;
import com.fashion.recommendation.ai.AiModelProvider;
import com.fashion.recommendation.ai.AiModelSettingRow;
import com.fashion.recommendation.ai.AiModelSettingsRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAiModelSettingsService {
    private final AiModelSettingsRepository settingsRepository;
    private final AiModelConfigurationService modelConfigurationService;
    private final AiApiKeyCipher cipher;
    private final AdminAuditRepository auditRepository;

    public AdminAiModelSettingsService(
            AiModelSettingsRepository settingsRepository,
            AiModelConfigurationService modelConfigurationService,
            AiApiKeyCipher cipher,
            AdminAuditRepository auditRepository) {
        this.settingsRepository = settingsRepository;
        this.modelConfigurationService = modelConfigurationService;
        this.cipher = cipher;
        this.auditRepository = auditRepository;
    }

    public List<AdminAiModelConfigView> list() {
        return Arrays.stream(AiModelCapability.values()).map(this::view).toList();
    }

    public AdminAiModelConfigView view(String capability) {
        return view(parseCapability(capability));
    }

    @Transactional
    public AdminAiModelConfigView update(
            String actor,
            String capabilityName,
            AdminAiModelConfigUpdateRequest request) {
        AiModelCapability capability = parseCapability(capabilityName);
        if (request.provider() == null || request.enabled() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型配置参数不合法");
        }
        String model = request.model() == null ? "" : request.model().trim();
        if (model.isEmpty() || model.length() > 120 || model.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型名称不合法");
        }
        if (capability == AiModelCapability.DAILY_IMAGE_GENERATION
                && request.provider() != AiModelProvider.DASHSCOPE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每日搭配图生成目前仅支持阿里云百炼");
        }
        String apiKey = request.apiKey();
        if (apiKey != null && apiKey.length() > 2048) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API Key 长度不能超过 2048 个字符");
        }
        if (apiKey != null && apiKey.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API Key 格式不合法");
        }
        boolean hasNewKey = StringUtils.hasText(apiKey);
        if (hasNewKey && request.clearApiKey()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能同时更新和清除 API Key");
        }
        if (hasNewKey && !cipher.isAvailable()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "API Key 加密尚未配置，请先设置 AI_SETTINGS_ENCRYPTION_KEY");
        }

        Optional<AiModelSettingRow> previous = settingsRepository.find(capability);
        if (previous.isPresent()
                && previous.get().provider() != request.provider()
                && StringUtils.hasText(previous.get().encryptedApiKey())
                && !hasNewKey
                && !request.clearApiKey()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "切换厂商时请提供新 API Key，或显式清除原厂商密钥");
        }
        String encryptedApiKey;
        if (hasNewKey) {
            encryptedApiKey = cipher.encrypt(apiKey.trim());
        } else if (request.clearApiKey()) {
            encryptedApiKey = null;
        } else {
            encryptedApiKey = previous.map(AiModelSettingRow::encryptedApiKey).orElse(null);
        }
        Instant updatedAt = Instant.now();
        settingsRepository.save(new AiModelSettingRow(
                capability,
                request.provider(),
                model,
                request.enabled(),
                encryptedApiKey,
                actor,
                updatedAt));

        String keyAction = hasNewKey ? "API Key 已更新"
                : request.clearApiKey() ? "API Key 覆盖已清除"
                : "API Key 保持不变";
        auditRepository.append(
                actor,
                "AI_MODEL_CONFIG_UPDATE",
                "AI_MODEL",
                capability.name(),
                "SUCCESS",
                "厂商=" + request.provider().label() + "；模型=" + model + "；启用="
                        + (request.enabled() ? "是" : "否") + "；" + keyAction);
        return view(capability);
    }

    @Transactional
    public AdminAiModelConfigView reset(String actor, String capabilityName) {
        AiModelCapability capability = parseCapability(capabilityName);
        if (settingsRepository.delete(capability)) {
            auditRepository.append(
                    actor,
                    "AI_MODEL_CONFIG_RESET",
                    "AI_MODEL",
                    capability.name(),
                    "SUCCESS",
                    "已恢复环境变量配置；未记录或返回密钥内容");
        }
        return view(capability);
    }

    private AdminAiModelConfigView view(AiModelCapability capability) {
        var runtime = modelConfigurationService.resolve(capability);
        return new AdminAiModelConfigView(
                capability.name(),
                capability.label(),
                runtime.provider().name(),
                runtime.provider().label(),
                runtime.model(),
                runtime.enabled(),
                modelConfigurationService.isEnvironmentEnabled(capability),
                modelConfigurationService.isEffectivelyEnabled(runtime),
                runtime.credentialSource(),
                runtime.credentialConfigured(),
                cipher.isAvailable(),
                runtime.managedOverride(),
                runtime.updatedAt());
    }

    private static AiModelCapability parseCapability(String value) {
        try {
            return AiModelCapability.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型能力不存在");
        }
    }
}
