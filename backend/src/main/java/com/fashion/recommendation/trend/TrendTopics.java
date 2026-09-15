package com.fashion.recommendation.trend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Transparent keyword classification, not an AI or platform ranking. */
public final class TrendTopics {
    private TrendTopics() {}
    private static final String[][] RULES = {
        {"通勤", "通勤", "office", "workwear", "tailoring", "blazer", "西装"},
        {"极简", "极简", "minimal", "quiet luxury", "简约"},
        {"丹宁", "丹宁", "牛仔", "denim", "jeans"},
        {"轻户外", "户外", "机能", "gorpcore", "outdoor"},
        {"运动休闲", "运动", "athleisure", "sneaker", "sport"},
        {"复古", "复古", "vintage", "retro", "怀旧"},
        {"街头", "街头", "street style", "streetwear"},
        {"针织", "针织", "knit", "cardigan", "sweater"},
        {"裙装", "裙", "dress", "skirt"},
        {"层次叠穿", "叠穿", "layering", "layered", "jacket", "coat"},
        {"配饰", "配饰", "accessor", "handbag", "jewelry", "belt"}
    };
    public static List<String> classify(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> tags = new ArrayList<>();
        for (String[] rule : RULES) for (int i = 1; i < rule.length; i++) {
            if (lower.contains(rule[i])) { tags.add(rule[0]); break; }
        }
        return tags.isEmpty() ? List.of("穿搭灵感") : tags.stream().limit(6).toList();
    }
    public static boolean fashion(String text) {
        return !classify(text).equals(List.of("穿搭灵感"))
                || text.toLowerCase(Locale.ROOT).matches("(?s).*(fashion|outfit|wearing|穿搭|搭配|时装|时尚).*");
    }
}
