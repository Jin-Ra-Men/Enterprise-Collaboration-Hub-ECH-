package com.ech.backend.api.aigateway;

import com.ech.backend.api.aigateway.dto.AiGatewayChatRequest;
import com.ech.backend.api.aigateway.dto.AiGatewayStatusResponse;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.exception.UnauthorizedException;
import com.ech.backend.common.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiGatewayController {

    private final AiGatewayService aiGatewayService;

    public AiGatewayController(AiGatewayService aiGatewayService) {
        this.aiGatewayService = aiGatewayService;
    }

    @GetMapping("/api/ai/gateway/status")
    public ApiResponse<AiGatewayStatusResponse> status(@AuthenticationPrincipal UserPrincipal principal) {
        requireAuth(principal);
        return ApiResponse.success(aiGatewayService.statusSnapshot());
    }

    @PostMapping("/api/ai/gateway/chat")
    public ResponseEntity<ApiResponse<?>> chat(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AiGatewayChatRequest request,
            HttpServletRequest httpRequest
    ) {
        requireAuth(principal);
        return aiGatewayService.chat(principal, request, httpRequest);
    }

    private static void requireAuth(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
    }
}
