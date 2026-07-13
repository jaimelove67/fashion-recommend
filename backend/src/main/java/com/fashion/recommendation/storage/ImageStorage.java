package com.fashion.recommendation.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {
    StoredImage store(String userId, MultipartFile file);

    StoredImageData read(String objectKey);

    void delete(String objectKey);
}
