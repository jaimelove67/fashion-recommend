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
        {"轻户外", "户外", "机能风", "gorpcore", "outdoor"},
        {"运动休闲", "运动", "athleisure", "sneaker", "sport"},
        {"复古", "复古", "vintage", "retro", "怀旧"},
        {"街头", "街头", "street style", "streetwear"},
        {"针织", "针织", "knit", "cardigan", "sweater"},
        {"裙装", "裙装", "连衣裙", "半身裙", "裙", "dress", "skirt"},
        {"层次叠穿", "叠穿", "layering", "layered", "jacket", "coat"},
        {"配饰", "配饰", "accessor", "handbag", "jewelry", "belt"}
    };

    /**
     * Unambiguous apparel and trend vocabulary, used to judge the short topic labels a platform hot
     * board publishes.
     *
     * <p>The rules above are written for article titles and bodies, where recall matters and a match
     * anywhere in a sentence is usually meaningful. A hot-board word is a two-to-twelve character
     * topic label instead, so the same substring matching over-triggers on ordinary news wording:
     * the live board really produced "才意识到一台苹果手机能换一万斤粮食" (the bigram 机能 sits inside
     * 手机能换) and would equally accept "街头采访" through 街头 and "裙带关系" through 裙. Board entries
     * are therefore admitted only on an explicit marker below. The list is deliberately narrow,
     * because one irrelevant entry labelled 穿搭 on the trend page costs more trust than a missing
     * borderline one.
     */
    private static final String[] BOARD_MARKERS = {
        // Explicit fashion and trend wording.
        "穿搭", "时装", "时尚", "潮流", "秀场", "走秀", "街拍", "造型", "ootd", "outfit", "fashion",
        "runway", "streetwear",
        // Garments and footwear, each specific enough to stand alone.
        "外套", "大衣", "风衣", "羽绒", "毛衣", "针织", "卫衣", "衬衫", "西装", "牛仔", "丹宁",
        "t恤", "连衣裙", "半身裙", "长裙", "短裙", "裙子", "裙装", "裤装", "阔腿裤", "牛仔裤",
        "靴子", "球鞋", "运动鞋", "高跟鞋", "皮鞋", "鞋",
        // Bags and accessories.
        "包包", "手袋", "背包", "配饰", "首饰", "耳环", "项链", "丝巾",
        // Beauty looks that the app's style vocabulary already covers.
        "美妆", "妆容", "发型", "口红"
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
        if (text == null) return false;
        return !classify(text).equals(List.of("穿搭灵感"))
                || text.toLowerCase(Locale.ROOT).matches("(?s).*(fashion|outfit|wearing|穿搭|搭配|时装|时尚).*");
    }

    /**
     * Whether a short hot-board topic label is fashion content. Stricter than {@link #fashion(String)}
     * on purpose; see {@link #BOARD_MARKERS}.
     */
    public static boolean boardFashion(String word) {
        if (word == null || word.isBlank()) return false;
        String lower = word.toLowerCase(Locale.ROOT);
        for (String marker : BOARD_MARKERS) if (lower.contains(marker)) return true;
        return false;
    }
}
