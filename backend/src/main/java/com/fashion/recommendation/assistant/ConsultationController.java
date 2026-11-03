package com.fashion.recommendation.assistant;

import com.fashion.recommendation.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assistant")
public class ConsultationController {
    private final ConsultationService service;

    public ConsultationController(ConsultationService service) {
        this.service = service;
    }

    @PostMapping("/consult")
    public ApiResponse<ConsultationResponse> consult(Principal principal, @Valid @RequestBody ConsultationRequest request) {
        return ApiResponse.ok(service.consult(principal.getName(), request));
    }
}
