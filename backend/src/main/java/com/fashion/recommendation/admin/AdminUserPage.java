package com.fashion.recommendation.admin;

import java.util.List;

public record AdminUserPage(
        List<AdminUser> items,
        long totalElements,
        int page,
        int size,
        boolean hasNext) {
}
