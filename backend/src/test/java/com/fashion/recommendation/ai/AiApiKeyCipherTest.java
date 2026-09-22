package com.fashion.recommendation.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fashion.recommendation.admin.AdminAiModelConfigUpdateRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AiApiKeyCipherTest {
    private static final String ROOT_KEY = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void encryptsWithFreshNonceAndRoundTripsWithoutStoringPlaintext() {
        AiApiKeyCipher cipher = new AiApiKeyCipher(ROOT_KEY);
        String secret = "sk-test-secret-value";

        String first = cipher.encrypt(secret);
        String second = cipher.encrypt(secret);

        assertTrue(first.startsWith("v1:"));
        assertNotEquals(secret, first);
        assertNotEquals(first, second);
        assertEquals(secret, cipher.decrypt(first).orElseThrow());
    }

    @Test
    void rejectsTamperedCiphertextAndWrongRootKey() {
        AiApiKeyCipher cipher = new AiApiKeyCipher(ROOT_KEY);
        String encrypted = cipher.encrypt("sk-test-secret-value");
        byte[] payload = Base64.getDecoder().decode(encrypted.substring("v1:".length()));
        payload[payload.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(payload);

        assertTrue(cipher.decrypt(tampered).isEmpty());
        String otherRootKey = Base64.getEncoder().encodeToString(
                "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));
        assertTrue(new AiApiKeyCipher(otherRootKey).decrypt(encrypted).isEmpty());
    }

    @Test
    void missingOrInvalidRootKeyNeverFallsBackToPlaintext() {
        AiApiKeyCipher missing = new AiApiKeyCipher(" ");
        assertFalse(missing.isAvailable());
        assertThrows(IllegalStateException.class, () -> missing.encrypt("sk-test-secret-value"));
        assertThrows(IllegalStateException.class, () -> new AiApiKeyCipher("not-base64"));
        assertThrows(IllegalStateException.class, () -> new AiApiKeyCipher(
                Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void updateRequestRedactsKeyFromGeneratedStringRepresentation() {
        String secret = "sk-test-secret-value";
        var request = new AdminAiModelConfigUpdateRequest(
                AiModelProvider.DASHSCOPE, "qwen-plus", true, secret, false);

        assertFalse(request.toString().contains(secret));
        assertTrue(request.toString().contains("<redacted>"));
    }
}
