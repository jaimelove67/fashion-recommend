package com.fashion.recommendation.admin;

import java.util.List;

public record AdminAuditLogPage(
        List<AdminAuditLog> items,
        long totalElements,
        int page,
        int size,
        boolean hasNext) {
}
