package com.ech.backend.api.channel;

import com.ech.backend.api.channel.dto.ChannelResponse;
import com.ech.backend.api.channel.dto.ChannelSummaryResponse;
import com.ech.backend.api.channel.dto.CreateChannelRequest;
import com.ech.backend.api.channel.dto.DelegateChannelManagerRequest;
import com.ech.backend.api.channel.dto.JoinChannelRequest;
import com.ech.backend.api.channel.dto.LeaveChannelRequest;
import com.ech.backend.api.channel.dto.RenameChannelRequest;
import com.ech.backend.api.channel.dto.RenameGroupDmRequest;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.exception.UnauthorizedException;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import com.ech.backend.common.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelService channelService;

    public ChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    @GetMapping
    public ApiResponse<List<ChannelSummaryResponse>> getMyChannels(@RequestParam String employeeNo) {
        return ApiResponse.success(channelService.getMyChannels(employeeNo));
    }

    @PostMapping
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<ChannelResponse> createChannel(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateChannelRequest request
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return ApiResponse.success(channelService.createChannel(request, principal));
    }

    @GetMapping("/{channelId}")
    public ApiResponse<ChannelResponse> getChannel(@PathVariable Long channelId) {
        return ApiResponse.success(channelService.getChannel(channelId));
    }

    @PostMapping("/{channelId}/members")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<ChannelResponse> joinChannel(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long channelId,
            @Valid @RequestBody JoinChannelRequest request
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return ApiResponse.success(channelService.joinChannel(channelId, principal, request));
    }

    /**
     * 채널 개설자(JWT 사원번호 = {@code created_by})만 호출 가능. {@code targetEmployeeNo} 멤버를 제거한다.
     */
    @DeleteMapping("/{channelId}/members")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<ChannelResponse> removeMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long channelId,
            @RequestParam("targetEmployeeNo") String targetEmployeeNo
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return ApiResponse.success(channelService.removeMember(channelId, principal, targetEmployeeNo));
    }

    @PutMapping("/{channelId}/dm-name")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<ChannelResponse> renameGroupDm(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long channelId,
            @Valid @RequestBody RenameGroupDmRequest request
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return ApiResponse.success(channelService.renameGroupDm(channelId, principal, request.name()));
    }

    @PutMapping("/{channelId}/name")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<ChannelResponse> renameChannel(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long channelId,
            @Valid @RequestBody RenameChannelRequest request
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return ApiResponse.success(channelService.renameChannel(channelId, principal, request.name()));
    }

    @PostMapping("/{channelId}/delegate-manager")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<ChannelResponse> delegateManager(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long channelId,
            @Valid @RequestBody DelegateChannelManagerRequest request
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return ApiResponse.success(channelService.delegateManager(channelId, principal, request.targetEmployeeNo()));
    }

    @PostMapping("/{channelId}/leave")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<Void> leaveChannel(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long channelId,
            @RequestBody(required = false) LeaveChannelRequest request
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        String delegateEmp = request == null ? null : request.delegateManagerEmployeeNo();
        channelService.leaveChannel(channelId, principal, delegateEmp);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{channelId}")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<Void> closeChannel(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long channelId
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        channelService.closeChannel(channelId, principal);
        return ApiResponse.success(null);
    }
}
