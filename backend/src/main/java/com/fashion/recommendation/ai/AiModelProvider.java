package com.fashion.recommendation.ai;

public enum AiModelProvider {
    DASHSCOPE("阿里云百炼"),
    OPENAI("OpenAI");

    private final String label;

    AiModelProvider(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String chatCompletionsEndpoint() {
        return switch (this) {
            case DASHSCOPE -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
            case OPENAI -> "https://api.openai.com/v1/chat/completions";
        };
    }

    public static AiModelProvider fromEndpoint(String endpoint) {
        if (endpoint != null && endpoint.startsWith("https://api.openai.com/")) {
            return OPENAI;
        }
        return DASHSCOPE;
    }
}
