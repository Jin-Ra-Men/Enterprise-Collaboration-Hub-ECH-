package com.ech.backend.api.aiassistant;

import com.ech.backend.api.aiassistant.dto.ChannelAiAssistantPreferenceResponse;
import com.ech.backend.api.aiassistant.dto.UpdateChannelAiAssistantPreferenceRequest;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.exception.UnauthorizedException;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import com.ech.backend.common.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChannelAiAssistantController {

    private final AiAssistantService aiAssistantService;

    public ChannelAiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @GetMapping("/api/channels/{channelId}/ai-assistant/preference")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<ChannelAiAssistantPreferenceResponse> getChannelPreference(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long channelId
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return ApiResponse.success(aiAssistantService.getChannelPreference(principal, channelId));
    }

    @PutMapping("/api/channels/{channelId}/ai-assistant/preference")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<ChannelAiAssistantPreferenceResponse> updateChannelPreference(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long channelId,
            @Valid @RequestBody UpdateChannelAiAssistantPreferenceRequest body
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return ApiResponse.success(aiAssistantService.updateChannelPreference(principal, channelId, body));
    }
}
