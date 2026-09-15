package com.fashion.recommendation.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminFeedbackStatusRequest(
        @NotBlank @Size(max = 16) String status) {
}
