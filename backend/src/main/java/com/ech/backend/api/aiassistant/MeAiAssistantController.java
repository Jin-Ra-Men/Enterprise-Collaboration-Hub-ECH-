package com.ech.backend.api.aiassistant;

import com.ech.backend.api.aiassistant.dto.AiSuggestionInboxItemResponse;
import com.ech.backend.api.aiassistant.dto.UpdateUserAiAssistantPreferenceRequest;
import com.ech.backend.api.aiassistant.dto.UserAiAssistantPreferenceResponse;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.exception.UnauthorizedException;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import com.ech.backend.common.security.UserPrincipal;
import com.ech.backend.domain.aiassistant.AiSuggestionInboxStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeAiAssistantController {

    private final AiAssistantService aiAssistantService;

    public MeAiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @GetMapping("/ai-assistant/preferences")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<UserAiAssistantPreferenceResponse> getUserPreference(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String employeeNo
    ) {
        requireAuth(principal);
        return ApiResponse.success(aiAssistantService.getUserPreference(principal, employeeNo));
    }

    @PutMapping("/ai-assistant/preferences")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<UserAiAssistantPreferenceResponse> updateUserPreference(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String employeeNo,
            @Valid @RequestBody UpdateUserAiAssistantPreferenceRequest body
    ) {
        requireAuth(principal);
        return ApiResponse.success(aiAssistantService.updateUserPreference(principal, employeeNo, body));
    }

    @GetMapping("/ai-suggestions")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<List<AiSuggestionInboxItemResponse>> listSuggestions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String employeeNo,
            @RequestParam(required = false) String status
    ) {
        requireAuth(principal);
        AiSuggestionInboxStatus st = parseStatus(status);
        return ApiResponse.success(aiAssistantService.listInbox(principal, employeeNo, st));
    }

    @PostMapping("/ai-suggestions/{suggestionId}/dismiss")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<Void> dismiss(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long suggestionId,
            @RequestParam(required = false) String employeeNo
    ) {
        requireAuth(principal);
        aiAssistantService.dismissInboxItem(principal, suggestionId, employeeNo);
        return ApiResponse.success(null);
    }

    @PostMapping("/ai-suggestions/{suggestionId}/acknowledge")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<Void> acknowledge(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long suggestionId,
            @RequestParam(required = false) String employeeNo
    ) {
        requireAuth(principal);
        aiAssistantService.acknowledgeInboxItem(principal, suggestionId, employeeNo);
        return ApiResponse.success(null);
    }

    private static void requireAuth(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
    }

    private static AiSuggestionInboxStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return AiSuggestionInboxStatus.PENDING;
        }
        try {
            return AiSuggestionInboxStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("status는 PENDING, DISMISSED, ACTED 중 하나여야 합니다.");
        }
    }
}
