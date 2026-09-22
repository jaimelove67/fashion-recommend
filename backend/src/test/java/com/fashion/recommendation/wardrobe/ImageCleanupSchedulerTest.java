package com.fashion.recommendation.wardrobe;

import com.fashion.recommendation.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ImageCleanupSchedulerTest {
    @Test
    void removesTaskAfterDeferredDeleteSucceeds() {
        ImageCleanupRepository repository = mock(ImageCleanupRepository.class);
        ImageStorage storage = mock(ImageStorage.class);
        org.mockito.Mockito.when(repository.findDue(50)).thenReturn(java.util.List.of("wardrobe/user/object.png"));
        ImageCleanupScheduler scheduler = new ImageCleanupScheduler(repository, storage);

        scheduler.retryFailedCleanup();

        InOrder order = inOrder(storage, repository);
        order.verify(storage).delete("wardrobe/user/object.png");
        order.verify(repository).remove("wardrobe/user/object.png");
    }

    @Test
    void recordsFailureAndKeepsTaskWhenDeferredDeleteFails() {
        ImageCleanupRepository repository = mock(ImageCleanupRepository.class);
        ImageStorage storage = mock(ImageStorage.class);
        org.mockito.Mockito.when(repository.findDue(50)).thenReturn(java.util.List.of("wardrobe/user/object.png"));
        org.mockito.Mockito.doThrow(new RuntimeException("MinIO unavailable"))
                .when(storage).delete("wardrobe/user/object.png");
        ImageCleanupScheduler scheduler = new ImageCleanupScheduler(repository, storage);

        scheduler.retryFailedCleanup();

        verify(repository).recordFailure("wardrobe/user/object.png");
        verify(repository, never()).remove("wardrobe/user/object.png");
    }
}
