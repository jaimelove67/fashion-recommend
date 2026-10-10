package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.wardrobe.WardrobeItem;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutfitPolicyTest {
    private WardrobeItem item(long id, String name, String category) {
        return new WardrobeItem(id, name, category, "黑色", null, null, Instant.now());
    }

    @Test
    void completeOutfitsNeedClothesAndNeverDuplicateCategories() {
        var top = item(1, "衬衫", "上装");
        var trousers = item(2, "长裤", "下装");
        var shoes = item(3, "鞋", "鞋履");
        var bag = item(4, "包", "配饰");
        var dress = item(5, "连衣裙", "连体装");
        assertFalse(OutfitPolicy.complete(List.of(shoes, bag)));
        assertTrue(OutfitPolicy.complete(List.of(top, trousers)));
        assertTrue(OutfitPolicy.complete(List.of(dress, shoes)));
        assertFalse(OutfitPolicy.complete(List.of(top, trousers, item(6, "T恤", "上装"))));
        assertFalse(OutfitPolicy.complete(List.of(dress, trousers)));
    }

    @Test
    void fallbackLimitsLayersKeepsLocksAndSupportsDresses() {
        var top = item(1, "衬衫", "上装");
        var trousers = item(2, "长裤", "下装");
        var outer = item(3, "外套", "外套");
        var shoes = item(4, "鞋", "鞋履");
        var bag = item(5, "包", "配饰");
        var clothes = List.of(top, trousers, outer, shoes, bag);
        assertFalse(OutfitPolicy.select(clothes, List.of(), 30).contains(outer));
        var cold = OutfitPolicy.select(clothes, List.of(), 12);
        assertEquals(4, cold.size());
        assertTrue(cold.contains(outer));
        var locked = OutfitPolicy.select(clothes, List.of(bag.id()), 20);
        assertTrue(locked.contains(bag));
        assertTrue(OutfitPolicy.complete(locked));
        var dress = item(6, "连衣裙", "连体装");
        assertTrue(OutfitPolicy.complete(OutfitPolicy.select(List.of(dress, shoes), List.of(), 24)));
    }

    @Test
    void temperatureChecksUseOnlyExplicitWardrobeDescriptions() {
        assertFalse(OutfitPolicy.suitable(item(1, "羽绒服", "外套"), 30));
        assertFalse(OutfitPolicy.suitable(item(2, "短裤", "下装"), 5));
        assertTrue(OutfitPolicy.suitable(item(3, "未知厚薄外套", "外套"), 20));
    }
}
