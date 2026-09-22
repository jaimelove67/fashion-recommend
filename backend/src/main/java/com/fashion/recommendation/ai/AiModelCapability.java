package com.fashion.recommendation.ai;

public enum AiModelCapability {
    WARDROBE_RECOGNITION("衣物图片识别"),
    OUTFIT_RECOMMENDATION("穿搭推荐"),
    DAILY_IMAGE_GENERATION("每日搭配图生成");

    private final String label;

    AiModelCapability(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
