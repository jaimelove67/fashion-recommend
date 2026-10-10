package com.fashion.recommendation.ai;

public enum AiModelCapability {
    WARDROBE_RECOGNITION("视觉识别（衣物与个人形象）"),
    OUTFIT_RECOMMENDATION("穿搭推荐"),
    DAILY_IMAGE_GENERATION("穿搭效果图生成");

    private final String label;

    AiModelCapability(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
