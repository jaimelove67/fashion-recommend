package com.fashion.recommendation.admin;

import com.fashion.recommendation.ai.AiApiKeyCipher;
import com.fashion.recommendation.ai.AiModelConfigurationService;
import com.fashion.recommendation.ai.AiModelProvider;
import com.fashion.recommendation.ai.AiModelSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminAiModelSettingsServiceTest {
    @Test
    void refusesToPersistAnApiKeyWhenEncryptionMasterKeyIsMissing() {
        AiModelSettingsRepository settingsRepository = mock(AiModelSettingsRepository.class);
        AiModelConfigurationService modelConfigurationService = mock(AiModelConfigurationService.class);
        AdminAuditRepository auditRepository = mock(AdminAuditRepository.class);
        AdminAiModelSettingsService service = new AdminAiModelSettingsService(
                settingsRepository,
                modelConfigurationService,
                new AiApiKeyCipher(""),
                auditRepository);
        AdminAiModelConfigUpdateRequest request = new AdminAiModelConfigUpdateRequest(
                AiModelProvider.DASHSCOPE,
                "qwen-plus",
                true,
                "sk-test-secret",
                false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.update("admin", "OUTFIT_RECOMMENDATION", request));

        assertEquals(503, exception.getStatusCode().value());
        verifyNoInteractions(settingsRepository, modelConfigurationService, auditRepository);
    }
}
