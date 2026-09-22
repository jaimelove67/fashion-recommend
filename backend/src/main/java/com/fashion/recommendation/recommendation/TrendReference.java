package com.fashion.recommendation.recommendation;

import java.util.List;

public record TrendReference(String id, String title, String sourceUrl, List<String> styleTags, String summary) {}
