package com.fashion.recommendation.ai;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiApiKeyCipher {
    private static final String VERSION = "v1:";
    private static final String AAD = "fashion-recommendation:admin-ai-model-settings:v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AiApiKeyCipher(@Value("${app.ai-settings.encryption-key:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            key = null;
            return;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("AI_SETTINGS_ENCRYPTION_KEY must be Base64-encoded 32 bytes");
        }
        if (decoded.length != 32) {
            Arrays.fill(decoded, (byte) 0);
            throw new IllegalStateException("AI_SETTINGS_ENCRYPTION_KEY must be Base64-encoded 32 bytes");
        }
        key = new SecretKeySpec(decoded, "AES");
        Arrays.fill(decoded, (byte) 0);
    }

    public boolean isAvailable() {
        return key != null;
    }

    public String encrypt(String value) {
        if (key == null) {
            throw new IllegalStateException("AI API Key encryption is not configured");
        }
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(AAD.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
            return VERSION + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AI API Key encryption failed");
        }
    }

    public Optional<String> decrypt(String value) {
        if (key == null || value == null || !value.startsWith(VERSION)) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(VERSION.length()));
            if (payload.length <= IV_BYTES) {
                return Optional.empty();
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(AAD.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Optional.of(new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
