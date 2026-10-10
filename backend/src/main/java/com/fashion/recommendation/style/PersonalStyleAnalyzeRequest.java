package com.fashion.recommendation.style;

import jakarta.validation.constraints.AssertTrue;

public record PersonalStyleAnalyzeRequest(
        @AssertTrue(message = "请同意将本次照片和资料发送给视觉模型") boolean allowAiAnalysis) { }
