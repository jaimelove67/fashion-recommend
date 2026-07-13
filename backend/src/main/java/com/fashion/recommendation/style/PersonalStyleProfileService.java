package com.fashion.recommendation.style;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PersonalStyleProfileService {
    private final StyleProfileRepository profileRepository;
    private final String modelName;

    public PersonalStyleProfileService(StyleProfileRepository profileRepository, @Value("${app.bailian.model}") String modelName) {
        this.profileRepository = profileRepository;
        this.modelName = modelName;
    }

    public StyleProfile current(String userId) {
        return profileRepository.findByUserId(userId).orElseGet(() -> {
            StyleProfile profile = buildProfile("你", List.of("极简", "通勤"), List.of("低饱和"), List.of("通勤"));
            profileRepository.save(userId, profile);
            return profile;
        });
    }

    public StyleProfile refresh(String userId, StyleProfileRefreshRequest request) {
        StyleProfile profile = buildProfile(
                request.displayName().trim(),
                normalized(request.stylePreferences()),
                normalized(request.colorPreferences()),
                normalized(request.occasions()));
        profileRepository.save(userId, profile);
        return profile;
    }

    private StyleProfile buildProfile(
            String displayName, List<String> stylePreferences, List<String> colorPreferences, List<String> occasions) {
        String joined = String.join(" ", stylePreferences);
        boolean likesMinimal = joined.contains("极简") || joined.contains("通勤");
        List<String> styles = stylePreferences.isEmpty()
                ? (likesMinimal ? List.of("极简通勤", "柔和剪裁") : List.of("轻机能", "日常层次"))
                : stylePreferences;
        List<String> colors = colorPreferences.isEmpty() ? List.of("雾蓝", "暖白", "石墨灰", "橄榄绿") : colorPreferences;
        return new StyleProfile(
                displayName,
                styles,
                colors,
                occasions,
                likesMinimal ? List.of("极简通勤", "柔和剪裁") : List.of("轻机能", "日常层次"),
                likesMinimal ? List.of("轻机能", "法式休闲") : List.of("极简通勤", "低饱和配色"),
                colors,
                List.of("短款外套", "直筒下装", "低跟皮鞋", "轻量托特包"),
                displayName + "的档案已按风格、颜色和场合偏好持久化更新。",
                modelName + " (development fallback)",
                Instant.now(),
                false);
    }

    private static List<String> normalized(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
    }
}
