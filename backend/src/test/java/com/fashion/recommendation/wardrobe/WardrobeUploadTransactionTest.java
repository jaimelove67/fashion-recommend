package com.fashion.recommendation.wardrobe;

import com.fashion.recommendation.recognition.GarmentRecognitionResult;
import com.fashion.recommendation.recognition.GarmentRecognitionService;
import com.fashion.recommendation.storage.ImageStorage;
import com.fashion.recommendation.storage.StoredImage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(WardrobeUploadTransactionTest.UploadConfiguration.class)
class WardrobeUploadTransactionTest {
    @Autowired private WardrobeService service;
    @Autowired private HikariDataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ImageStorage storage;
    @Autowired private GarmentRecognitionService recognition;
    @Autowired private CommitFailureTransactionManager transactions;

    @BeforeEach
    void prepare() {
        reset(storage, recognition);
        transactions.failCommit = false;
        jdbc.execute("ALTER TABLE wardrobe_items DROP CONSTRAINT IF EXISTS reject_image_url");
        jdbc.update("DELETE FROM wardrobe_items");
        when(storage.store(anyString(), any())).thenReturn(new StoredImage("upload-object", "image/png"));
        when(recognition.recognize(any())).thenReturn(Optional.of(
                new GarmentRecognitionResult("蓝色衬衫", "上装", "蓝色", "通勤")));
    }

    @Test
    void keepsDatabaseAvailableDuringStorageAndRecognitionWithASingleConnection() {
        when(storage.store(anyString(), any())).thenAnswer(invocation -> {
            assertDatabaseAvailable();
            return new StoredImage("upload-object", "image/png");
        });
        when(recognition.recognize(any())).thenAnswer(invocation -> {
            assertDatabaseAvailable();
            return Optional.of(new GarmentRecognitionResult("蓝色衬衫", "上装", "蓝色", "通勤"));
        });

        WardrobeItem item = upload();

        assertEquals("RECOGNIZED", item.recognitionStatus());
        assertEquals("/api/v1/me/wardrobe/" + item.id() + "/image", item.imageUrl());
        assertEquals(1, service.list("upload-owner").size());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void rollsBackTheInsertedItemAndDeletesItsImageWhenTheImageUrlWriteFails() {
        // The initial INSERT succeeds; the following image URL UPDATE must fail.
        jdbc.execute("ALTER TABLE wardrobe_items ADD CONSTRAINT reject_image_url CHECK (image_url IS NULL)");

        assertThrows(DataIntegrityViolationException.class, this::upload);

        assertTrue(service.list("upload-owner").isEmpty());
        verify(storage).delete("upload-object");
    }

    @Test
    void deletesTheStoredImageWhenTheDatabaseTransactionCannotCommit() {
        transactions.failCommit = true;

        assertThrows(TransactionSystemException.class, this::upload);

        assertTrue(service.list("upload-owner").isEmpty());
        verify(storage).delete("upload-object");
    }

    private WardrobeItem upload() {
        return service.upload("upload-owner",
                new MockMultipartFile("image", "shirt.png", "image/png", new byte[] {1}),
                null, null, null, null, true);
    }

    private void assertDatabaseAvailable() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                "External upload work must not keep a database transaction open");
        assertEquals(0, dataSource.getHikariPoolMXBean().getActiveConnections());
        assertEquals(1, jdbc.queryForObject("SELECT 1", Integer.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import(WardrobeService.class)
    static class UploadConfiguration {
        @Bean(destroyMethod = "close")
        HikariDataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:h2:mem:upload-transaction-" + UUID.randomUUID() + ";MODE=PostgreSQL");
            config.setMaximumPoolSize(1);
            config.setConnectionTimeout(500);
            HikariDataSource source = new HikariDataSource(config);
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__baseline_schema.sql"))
                    .execute(source);
            return source;
        }

        @Bean JdbcTemplate jdbcTemplate(HikariDataSource source) { return new JdbcTemplate(source); }
        @Bean WardrobeRepository wardrobeRepository(JdbcTemplate jdbc) { return new WardrobeRepository(jdbc); }
        @Bean ImageCleanupRepository imageCleanupRepository(JdbcTemplate jdbc) { return new ImageCleanupRepository(jdbc); }
        @Bean ImageStorage imageStorage() { return mock(ImageStorage.class); }
        @Bean GarmentRecognitionService recognitionService() { return mock(GarmentRecognitionService.class); }
        @Bean CommitFailureTransactionManager transactionManager(HikariDataSource source) {
            return new CommitFailureTransactionManager(source);
        }
        @Bean TransactionTemplate transactionTemplate(CommitFailureTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }

    static class CommitFailureTransactionManager extends JdbcTransactionManager {
        boolean failCommit;

        CommitFailureTransactionManager(HikariDataSource source) {
            super(source);
            setRollbackOnCommitFailure(true);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new TransactionSystemException("Simulated database commit failure");
            super.doCommit(status);
        }
    }
}
