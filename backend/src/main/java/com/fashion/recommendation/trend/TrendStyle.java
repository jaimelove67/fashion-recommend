package com.fashion.recommendation.trend;

import java.util.List;

public record TrendStyle(String name, int contentCount, List<String> platforms, String imageUrl, String description) {}
