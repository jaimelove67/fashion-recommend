package com.fashion.recommendation.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ConsultationRequest(
        @NotBlank @Size(max = 2000) String question,
        @Size(max = 10) List<@NotNull @Valid Turn> history) {
    public record Turn(
            @NotNull @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 2000) String text) {}
}
