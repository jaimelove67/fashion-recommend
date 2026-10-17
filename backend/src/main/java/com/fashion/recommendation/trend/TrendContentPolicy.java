package com.fashion.recommendation.trend;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Text screening is preliminary; publication also requires review of the actual images. */
public final class TrendContentPolicy {
    private TrendContentPolicy() {}

    private static final String[] BODY_OR_SUGGESTIVE_MARKERS = {
        "身体特写", "胸部特写", "臀部特写", "事业线特写", "秀胸", "秀臀", "爆乳", "福利姬",
        "擦边", "性暗示", "挑逗写真", "诱惑写真", "情趣内衣", "露点", "裸聊", "nsfw", "onlyfans"
    };

    public static String exclusionReason(TrendItem item) {
        String text = String.join(" ", item.title(), item.summary() == null ? "" : item.summary(),
                String.join(" ", item.topicTags()), item.evidence() == null || item.evidence().author() == null
                        ? "" : item.evidence().author()).toLowerCase(Locale.ROOT);
        for (String marker : BODY_OR_SUGGESTIVE_MARKERS) {
            if (text.contains(marker)) return "不符合内容范围（身体特写或性暗示）";
        }
        return null;
    }

    public static boolean hasReviewableImage(TrendItem item) {
        return item.evidence() != null && item.evidence().mediaType() != null
                && List.of("image", "photo", "post").contains(item.evidence().mediaType().toLowerCase(Locale.ROOT))
                && ((item.imageUrl() != null && !item.imageUrl().isBlank()) || !item.evidence().images().isEmpty());
    }

    /** Changes to content or any displayed image invalidate approval; counter refreshes do not. */
    public static String reviewHash(TrendItem item, ObjectMapper mapper) {
        var e = item.evidence();
        var content = new ReviewContent("outfit-content-v1", item.id(), item.platform(), item.sourceUrl(),
                item.title(), item.summary(), item.topicTags(), item.imageUrl(), e == null ? null : e.author(),
                e == null ? null : e.mediaType(), e == null ? List.of() : e.images(),
                e == null ? null : e.fullBodyImageUrl());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(content)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法核对趋势内容版本", exception);
        }
    }

    private record ReviewContent(String policyVersion, String id, String platform, String sourceUrl,
            String title, String summary, List<String> tags, String imageUrl, String author,
            String mediaType, List<String> images, String fullBodyImageUrl) {}
}
