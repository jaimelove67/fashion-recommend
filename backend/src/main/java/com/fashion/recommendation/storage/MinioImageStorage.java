package com.fashion.recommendation.storage;

import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MinioImageStorage implements ImageStorage {
    private final MinioClient minioClient;
    private final String bucket;

    public MinioImageStorage(
            MinioClient minioClient,
            @Value("${app.storage.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public StoredImage store(String userId, MultipartFile file) {
        String extension = extension(file.getOriginalFilename(), file.getContentType());
        String objectKey = "wardrobe/" + safeSegment(userId) + "/" + UUID.randomUUID() + extension;
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return new StoredImage(objectKey, file.getContentType());
        } catch (Exception exception) {
            throw new StorageUnavailableException("图片存储服务暂时不可用", exception);
        }
    }

    @Override
    public StoredImageData read(String objectKey) {
        try {
            String contentType = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build()).contentType();
            try (InputStream input = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build())) {
                return new StoredImageData(input.readAllBytes(), contentType);
            }
        } catch (Exception exception) {
            throw new StorageUnavailableException("图片读取失败", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new StorageUnavailableException("图片删除失败", exception);
        }
    }

    private void ensureBucket() throws Exception {
        try {
            if (!minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (ErrorResponseException exception) {
            if (!"BucketAlreadyOwnedByYou".equals(exception.errorResponse().code())) {
                throw exception;
            }
        }
    }

    private static String safeSegment(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static String extension(String originalName, String contentType) {
        String candidate = StringUtils.getFilenameExtension(originalName);
        if (StringUtils.hasText(candidate) && candidate.length() <= 5) {
            return "." + candidate.toLowerCase(Locale.ROOT);
        }
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
