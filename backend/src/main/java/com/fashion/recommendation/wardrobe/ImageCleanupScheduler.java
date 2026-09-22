package com.fashion.recommendation.wardrobe;

import com.fashion.recommendation.storage.ImageStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ImageCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(ImageCleanupScheduler.class);
    private final ImageCleanupRepository cleanupRepository;
    private final ImageStorage imageStorage;

    public ImageCleanupScheduler(ImageCleanupRepository cleanupRepository, ImageStorage imageStorage) {
        this.cleanupRepository = cleanupRepository;
        this.imageStorage = imageStorage;
    }

    @Scheduled(fixedDelayString = "${app.storage.cleanup-interval:60000}")
    public void retryFailedCleanup() {
        for (String objectKey : cleanupRepository.findDue(50)) {
            try {
                imageStorage.delete(objectKey);
                cleanupRepository.remove(objectKey);
            } catch (RuntimeException exception) {
                cleanupRepository.recordFailure(objectKey);
                log.warn("Deferred wardrobe image cleanup failed, objectKey={}, reason={}",
                        objectKey, exception.getMessage());
            }
        }
    }
}
