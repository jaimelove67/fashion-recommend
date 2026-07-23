package com.fashion.recommendation.wardrobe;

import com.fashion.recommendation.recognition.GarmentRecognitionResult;
import com.fashion.recommendation.recognition.GarmentRecognitionService;
import com.fashion.recommendation.storage.ImageStorage;
import com.fashion.recommendation.storage.StoredImage;
import com.fashion.recommendation.storage.StoredImageData;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WardrobeService {
    private final WardrobeRepository wardrobeRepository;
    private final ImageStorage imageStorage;
    private final GarmentRecognitionService recognitionService;
    private final long maxFileSize;

    public WardrobeService(
            WardrobeRepository wardrobeRepository,
            ImageStorage imageStorage,
            GarmentRecognitionService recognitionService,
            @Value("${app.storage.max-file-size:10485760}") long maxFileSize) {
        this.wardrobeRepository = wardrobeRepository;
        this.imageStorage = imageStorage;
        this.recognitionService = recognitionService;
        this.maxFileSize = maxFileSize;
    }

    public List<WardrobeItem> list(String userId) {
        return wardrobeRepository.findByUserId(userId);
    }

    public WardrobeItem create(String userId, WardrobeItemRequest request) {
        return wardrobeRepository.create(userId, request);
    }

    public WardrobeItem upload(
            String userId,
            MultipartFile image,
            String manualName,
            String manualCategory,
            String manualColor,
            String manualStyle,
            boolean allowAiRecognition) {
        validateImage(image);
        StoredImage stored = imageStorage.store(userId, image);
        Optional<GarmentRecognitionResult> recognition = Optional.empty();
        if (allowAiRecognition) {
            try {
                recognition = recognitionService.recognize(image);
            } catch (RuntimeException exception) {
                recognition = Optional.empty();
            }
        }

        GarmentRecognitionResult detected = recognition.orElse(new GarmentRecognitionResult("", "", "", ""));
        String name = choose(manualName, detected.name(), "待补充衣物");
        String category = choose(manualCategory, detected.category(), "待识别");
        String color = choose(manualColor, detected.color(), "待识别");
        String style = chooseNullable(manualStyle, detected.style());
        boolean complete = StringUtils.hasText(manualName) || StringUtils.hasText(detected.name());
        complete &= isRecognizedCategory(category) && !"待识别".equals(color);
        String status = complete ? (recognition.isPresent() ? "RECOGNIZED" : "MANUAL_CORRECTED") : "NEEDS_MANUAL_REVIEW";
        String message = complete ? null : allowAiRecognition
                ? "AI 识别未得到完整信息，请手动确认类别、颜色和名称"
                : "请手动确认类别、颜色和名称";
        try {
            WardrobeItem item = wardrobeRepository.create(userId,
                    new WardrobeItemRequest(name, category, color, style, null),
                    stored.objectKey(), status, message);
            String imageUrl = "/api/v1/me/wardrobe/" + item.id() + "/image";
            wardrobeRepository.updateImageUrl(item.id(), userId, imageUrl);
            return wardrobeRepository.findByIdForUser(item.id(), userId).orElseThrow();
        } catch (RuntimeException exception) {
            imageStorage.delete(stored.objectKey());
            throw exception;
        }
    }

    public WardrobeItem update(String userId, Long itemId, WardrobeItemRequest request) {
        WardrobeItem item = wardrobeRepository.updateMetadata(itemId, userId, request);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "衣物不存在或不属于当前用户");
        }
        return item;
    }

    public StoredImageData readImage(String userId, Long itemId) {
        WardrobeItem item = wardrobeRepository.findByImageForUser(itemId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "衣物图片不存在"));
        return imageStorage.read(item.imageObjectKey());
    }

    public void delete(String userId, Long itemId) {
        WardrobeItem item = wardrobeRepository.findByIdForUser(itemId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "衣物不存在或不属于当前用户"));
        if (item.imageObjectKey() != null) {
            imageStorage.delete(item.imageObjectKey());
        }
        if (!wardrobeRepository.delete(itemId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "衣物不存在或不属于当前用户");
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择衣物图片");
        }
        if (image.getSize() > maxFileSize) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片大小不能超过 10 MB");
        }
        if (!List.of("image/jpeg", "image/png", "image/webp").contains(image.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 JPG、PNG 或 WEBP 图片");
        }
    }

    private static String choose(String manual, String detected, String fallback) {
        return StringUtils.hasText(manual) ? manual.trim() : StringUtils.hasText(detected) ? detected.trim() : fallback;
    }

    private static String chooseNullable(String manual, String detected) {
        String value = choose(manual, detected, "");
        return StringUtils.hasText(value) ? value : null;
    }

    private static boolean isRecognizedCategory(String category) {
        return List.of("上装", "下装", "鞋履", "外套", "配饰").contains(category);
    }
}
