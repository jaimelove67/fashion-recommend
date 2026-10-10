package com.fashion.recommendation.style;

import com.fashion.recommendation.storage.ImageStorage;
import com.fashion.recommendation.storage.StoredImage;
import com.fashion.recommendation.storage.StoredImageData;
import com.fashion.recommendation.wardrobe.ImageCleanupRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PersonalStyleProfileService {
    private final StyleProfileRepository profileRepository;
    private final ImageStorage imageStorage;
    private final ImageCleanupRepository imageCleanupRepository;
    private final PersonalStyleAnalysisClient analysisClient;
    private final TransactionTemplate transactionTemplate;
    private final long maxFileSize;

    public PersonalStyleProfileService(StyleProfileRepository profileRepository, ImageStorage imageStorage,
            ImageCleanupRepository imageCleanupRepository, PersonalStyleAnalysisClient analysisClient,
            TransactionTemplate transactionTemplate, @Value("${app.storage.max-file-size:10485760}") long maxFileSize) {
        this.profileRepository = profileRepository;
        this.imageStorage = imageStorage;
        this.imageCleanupRepository = imageCleanupRepository;
        this.analysisClient = analysisClient;
        this.transactionTemplate = transactionTemplate;
        this.maxFileSize = maxFileSize;
    }

    public StyleProfile current(String userId) {
        if (profileRepository.findByUserId(userId).isEmpty()) {
            profileRepository.createIfMissing(userId,
                    buildProfile("你", null, List.of(), List.of(), List.of()));
        }
        return profileRepository.findByUserId(userId).orElseThrow();
    }

    public StyleProfile refresh(String userId, StyleProfileRefreshRequest request) {
        StyleProfile previous = current(userId);
        StyleProfile base = buildProfile(request.displayName().trim(), normalizeGender(request.gender()),
                normalized(request.stylePreferences()), normalized(request.colorPreferences()), normalized(request.occasions()));
        List<String> avoid = request.avoidPreferences() == null ? previous.avoidPreferences() : normalized(request.avoidPreferences());
        boolean preferencesChanged = !base.stylePreferences().equals(previous.stylePreferences())
                || !base.colorPreferences().equals(previous.colorPreferences()) || !base.occasions().equals(previous.occasions())
                || !avoid.equals(previous.avoidPreferences());
        boolean confirmed = request.confirmPreferences() == null
                ? previous.preferencesConfirmed() && !preferencesChanged : request.confirmPreferences();
        base = base.withPreferenceDetails(avoid, confirmed);
        Double height = measurement(request.heightCm(), previous.heightCm());
        Double weight = measurement(request.weightKg(), previous.weightKg());
        boolean changed = !Objects.equals(height, previous.heightCm()) || !Objects.equals(weight, previous.weightKg())
                || !Objects.equals(base.gender(), previous.gender())
                || preferencesChanged;
        boolean stale = previous.analysis() != null && (previous.stale() || changed);
        profileRepository.save(userId, attach(base, height, weight, previous.photoObjectKey(), previous.analysis(),
                previous.analysisSource(), previous.analysisModelName(), previous.analysisUpdatedAt(), stale, previous.revision(),
                previous.usePersonalPhotoForOutfit()));
        return current(userId);
    }

    public StyleProfile uploadPhoto(String userId, MultipartFile photo) {
        validatePhoto(photo);
        StyleProfile previous = current(userId);
        StoredImage stored = imageStorage.store(userId, photo);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                profileRepository.save(userId, attach(previous, previous.heightCm(), previous.weightKg(), stored.objectKey(),
                        previous.analysis(), previous.analysisSource(), previous.analysisModelName(), previous.analysisUpdatedAt(),
                        previous.analysis() != null, previous.revision(), false));
                if (previous.photoObjectKey() != null) imageCleanupRepository.enqueue(previous.photoObjectKey());
            });
        } catch (RuntimeException exception) {
            try { imageStorage.delete(stored.objectKey()); } catch (RuntimeException ignored) { }
            throw exception;
        }
        return current(userId);
    }

    public StoredImageData readPhoto(String userId) {
        String key = current(userId).photoObjectKey();
        if (key == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "个人照片尚未上传");
        return imageStorage.read(key);
    }

    public StoredImageData readOutfitPhoto(String userId, String expectedPhotoKey) {
        StyleProfile profile = current(userId);
        if (!profile.usePersonalPhotoForOutfit() || !Objects.equals(profile.photoObjectKey(), expectedPhotoKey) || profile.photoObjectKey() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "人物设置或个人照片已更新，请重新生成。");
        }
        // Read the checked snapshot's object key so a concurrent upload cannot substitute an unapproved photo.
        return imageStorage.read(profile.photoObjectKey());
    }

    public StyleProfile updateOutfitModelPreference(String userId, boolean usePersonalPhoto) {
        StyleProfile previous = current(userId);
        if (usePersonalPhoto && previous.photoObjectKey() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "请先上传个人照片，再选择使用本人形象。");
        }
        if (previous.usePersonalPhotoForOutfit() == usePersonalPhoto) return previous;
        profileRepository.save(userId, attach(previous, previous.heightCm(), previous.weightKg(), previous.photoObjectKey(),
                previous.analysis(), previous.analysisSource(), previous.analysisModelName(), previous.analysisUpdatedAt(),
                previous.stale(), previous.revision(), usePersonalPhoto));
        return current(userId);
    }

    public StyleProfile analyze(String userId, boolean allowAiAnalysis) {
        if (!allowAiAnalysis) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请同意将本次照片和资料发送给视觉模型");
        StyleProfile previous = current(userId);
        if (previous.heightCm() == null || previous.weightKg() == null || previous.photoObjectKey() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "请先保存身高、体重和个人照片");
        }
        // Storage reads and paid provider calls run outside the database write transaction.
        PersonalStyleAnalysisResult result = analysisClient.analyze(previous, imageStorage.read(previous.photoObjectKey()));
        if (result == null || result.analysis() == null || !result.analysis().hasObservations()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "未获得有效形象分析，请换一张清楚的照片或手动填写");
        }
        profileRepository.save(userId, attach(previous, previous.heightCm(), previous.weightKg(), previous.photoObjectKey(),
                result.analysis(), "MODEL", result.modelName(), Instant.now(), false, previous.revision()));
        return current(userId);
    }

    public StyleProfile correctAnalysis(String userId, PersonalStyleAnalysis analysis) {
        if (!analysis.hasContent()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写至少一项形象特征或穿衣建议");
        StyleProfile previous = current(userId);
        profileRepository.save(userId, attach(previous, previous.heightCm(), previous.weightKg(), previous.photoObjectKey(),
                analysis, "MANUAL", null, Instant.now(), false, previous.revision()));
        return current(userId);
    }

    private static StyleProfile attach(StyleProfile profile, Double height, Double weight, String photoKey,
            PersonalStyleAnalysis analysis, String source, String analysisModel, Instant analysisTime, boolean stale, long revision) {
        return attach(profile, height, weight, photoKey, analysis, source, analysisModel, analysisTime, stale, revision,
                profile.usePersonalPhotoForOutfit());
    }

    private static StyleProfile attach(StyleProfile profile, Double height, Double weight, String photoKey,
            PersonalStyleAnalysis analysis, String source, String analysisModel, Instant analysisTime, boolean stale,
            long revision, boolean usePersonalPhoto) {
        StyleProfile base = buildProfile(profile.displayName(), profile.gender(), profile.stylePreferences(),
                profile.colorPreferences(), profile.occasions());
        PersonalStyleAnalysis usable = stale ? null : analysis;
        return new StyleProfile(base.displayName(), base.gender(), base.stylePreferences(), base.colorPreferences(), base.occasions(),
                usable == null || usable.styleTags().isEmpty() ? base.styleTags() : usable.styleTags(),
                usable == null ? base.tryStyleTags() : usable.tryStyleTags(),
                usable == null || usable.colorSuggestions().isEmpty() ? base.colorSuggestions() : usable.colorSuggestions(),
                usable == null ? base.itemSuggestions() : usable.itemSuggestions(),
                usable == null || usable.reasonSummary().isBlank() ? base.reasonSummary() : usable.reasonSummary(),
                usable == null ? base.modelName() : "MODEL".equals(source) ? analysisModel : "manual-profile",
                Instant.now(), stale, height, weight, null, photoKey, analysis, source, analysisModel, analysisTime, revision,
                usePersonalPhoto, profile.avoidPreferences(), profile.preferencesConfirmed());
    }

    private static StyleProfile buildProfile(String displayName, String gender, List<String> styles, List<String> colors, List<String> occasions) {
        return new StyleProfile(displayName, gender, styles, colors, occasions, styles, List.of(), colors, List.of(),
                styles.isEmpty() && colors.isEmpty() && occasions.isEmpty()
                        ? "穿搭偏好尚未建立，可直接选择或从搭配参考开始探索；也可先按天气、场合和衣橱生成推荐。"
                        : "已记录风格、颜色和场合偏好；可在穿搭偏好中核对，也可上传照片分析或手动填写形象特征。",
                "development-profile-rules", Instant.now(), false);
    }

    private void validatePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择个人照片");
        if (photo.getSize() > maxFileSize) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "照片大小不能超过 10 MB");
        try {
            byte[] bytes = photo.getBytes();
            String type = photo.getContentType();
            boolean png = "image/png".equals(type) && bytes.length >= 8
                    && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            boolean jpeg = "image/jpeg".equals(type) && bytes.length >= 3
                    && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
            boolean webp = "image/webp".equals(type) && bytes.length >= 12
                    && new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            if (!png && !jpeg && !webp) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的 JPG、PNG 或 WEBP 照片");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取个人照片");
        }
    }

    private static Double measurement(Double value, Double fallback) {
        if (value == null) return fallback;
        return Math.round(value * 10.0) / 10.0;
    }
    private static String normalizeGender(String value) { return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null; }
    private static List<String> normalized(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
    }
}
