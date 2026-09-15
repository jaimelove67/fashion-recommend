package com.fashion.recommendation.admin;

import jakarta.validation.constraints.NotNull;

public record AdminUserStatusRequest(@NotNull Boolean enabled) {
}
