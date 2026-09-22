package com.fashion.recommendation.ai;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiModelSettingsRepository {
    private final JdbcTemplate jdbcTemplate;

    public AiModelSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AiModelSettingRow> find(AiModelCapability capability) {
        return jdbcTemplate.query(
                "SELECT capability, provider, model_name, enabled, api_key_ciphertext, updated_by, updated_at "
                        + "FROM admin_ai_model_settings WHERE capability = ?",
                (resultSet, rowNum) -> mapRow(
                        resultSet.getString("capability"),
                        resultSet.getString("provider"),
                        resultSet.getString("model_name"),
                        resultSet.getBoolean("enabled"),
                        resultSet.getString("api_key_ciphertext"),
                        resultSet.getString("updated_by"),
                        resultSet.getTimestamp("updated_at")),
                capability.name()).stream().findFirst();
    }

    public List<AiModelSettingRow> findAll() {
        return jdbcTemplate.query(
                "SELECT capability, provider, model_name, enabled, api_key_ciphertext, updated_by, updated_at "
                        + "FROM admin_ai_model_settings ORDER BY capability",
                (resultSet, rowNum) -> mapRow(
                        resultSet.getString("capability"),
                        resultSet.getString("provider"),
                        resultSet.getString("model_name"),
                        resultSet.getBoolean("enabled"),
                        resultSet.getString("api_key_ciphertext"),
                        resultSet.getString("updated_by"),
                        resultSet.getTimestamp("updated_at")));
    }

    public void save(AiModelSettingRow setting) {
        Timestamp updatedAt = Timestamp.from(setting.updatedAt());
        int updated = update(setting, updatedAt);
        if (updated > 0) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO admin_ai_model_settings "
                            + "(capability, provider, model_name, enabled, api_key_ciphertext, updated_by, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    setting.capability().name(), setting.provider().name(), setting.model(), setting.enabled(),
                    setting.encryptedApiKey(), setting.updatedBy(), updatedAt);
        } catch (DuplicateKeyException exception) {
            if (update(setting, updatedAt) == 0) {
                throw exception;
            }
        }
    }

    public boolean delete(AiModelCapability capability) {
        return jdbcTemplate.update(
                "DELETE FROM admin_ai_model_settings WHERE capability = ?", capability.name()) > 0;
    }

    private int update(AiModelSettingRow setting, Timestamp updatedAt) {
        return jdbcTemplate.update(
                "UPDATE admin_ai_model_settings SET provider = ?, model_name = ?, enabled = ?, "
                        + "api_key_ciphertext = ?, updated_by = ?, updated_at = ? WHERE capability = ?",
                setting.provider().name(), setting.model(), setting.enabled(), setting.encryptedApiKey(),
                setting.updatedBy(), updatedAt, setting.capability().name());
    }

    private static AiModelSettingRow mapRow(
            String capability,
            String provider,
            String model,
            boolean enabled,
            String encryptedApiKey,
            String updatedBy,
            Timestamp updatedAt) {
        return new AiModelSettingRow(
                AiModelCapability.valueOf(capability),
                AiModelProvider.valueOf(provider),
                model,
                enabled,
                encryptedApiKey,
                updatedBy,
                updatedAt == null ? Instant.EPOCH : updatedAt.toInstant());
    }
}
