package com.fashion.recommendation.admin;

import java.util.List;

public record AdminFeedbackPage(
        List<AdminFeedback> items,
        long totalElements,
        int page,
        int size,
        boolean hasNext) {
}
