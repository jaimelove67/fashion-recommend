package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.wardrobe.WardrobeItem;
import java.util.ArrayList;
import java.util.List;

/** Shared constraints for model results and the deterministic fallback. */
final class OutfitPolicy {
    private OutfitPolicy() {}

    static String category(WardrobeItem item) {
        String value = item.category() == null ? "" : item.category().trim();
        if (value.contains("连衣裙") || value.contains("连体")) return "连体装";
        for (String type : List.of("外套", "上装", "下装", "鞋履", "配饰")) {
            if (value.contains(type) || (type.equals("鞋履") && value.contains("鞋"))) return type;
        }
        return value;
    }

    static boolean complete(List<WardrobeItem> items) {
        List<String> categories = items.stream().map(OutfitPolicy::category).toList();
        return items.size() >= 2 && items.size() <= 4
                && categories.stream().distinct().count() == categories.size()
                && (categories.contains("连体装")
                    ? !categories.contains("上装") && !categories.contains("下装")
                    : categories.contains("上装") && categories.contains("下装"));
    }

    static boolean suitable(WardrobeItem item, double temperature) {
        String description = item.name() + " " + item.style();
        // Only reject thermal properties explicitly recorded in the wardrobe.
        if (temperature >= 26 && description.matches(".*(羽绒|棉服|棉袄|厚毛衣|加绒|羊绒围巾).*")) return false;
        return temperature > 10 || !description.matches(".*(短裤|超短裙|凉鞋|人字拖).*" );
    }

    static List<WardrobeItem> select(List<WardrobeItem> ranked, List<Long> locked, double temperature) {
        List<WardrobeItem> chosen = new ArrayList<>(ranked.stream().filter(item -> locked.contains(item.id())).toList());
        boolean separateLocked = chosen.stream().anyMatch(item -> List.of("上装", "下装").contains(category(item)));
        boolean dressLocked = chosen.stream().anyMatch(item -> category(item).equals("连体装"));
        boolean separateAvailable = ranked.stream().anyMatch(item -> category(item).equals("上装"))
                && ranked.stream().anyMatch(item -> category(item).equals("下装"));
        if (dressLocked || (!separateLocked && !separateAvailable)) addCategory(chosen, ranked, "连体装");
        else {
            addCategory(chosen, ranked, "上装");
            addCategory(chosen, ranked, "下装");
        }
        if (temperature < 18) addCategory(chosen, ranked, "外套");
        addCategory(chosen, ranked, "鞋履");
        addCategory(chosen, ranked, "配饰");
        return chosen;
    }

    private static void addCategory(List<WardrobeItem> chosen, List<WardrobeItem> ranked, String type) {
        if (chosen.size() >= 4 || chosen.stream().anyMatch(item -> category(item).equals(type))) return;
        ranked.stream().filter(item -> category(item).equals(type)).findFirst().ifPresent(chosen::add);
    }
}
