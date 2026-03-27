package com.ech.backend.api.channel;

import com.ech.backend.api.channel.dto.ChannelResponse;
import com.ech.backend.api.channel.dto.ChannelSummaryResponse;
import com.ech.backend.api.channel.dto.CreateChannelRequest;
import com.ech.backend.api.channel.dto.JoinChannelRequest;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    public ApiResponse<ChannelResponse> createChannel(@Valid @RequestBody CreateChannelRequest request) {
        return ApiResponse.success(channelService.createChannel(request));
    }

    @GetMapping("/{channelId}")
    public ApiResponse<ChannelResponse> getChannel(@PathVariable Long channelId) {
        return ApiResponse.success(channelService.getChannel(channelId));
    }

    @PostMapping("/{channelId}/members")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<ChannelResponse> joinChannel(
            @PathVariable Long channelId,
            @Valid @RequestBody JoinChannelRequest request
    ) {
        return ApiResponse.success(channelService.joinChannel(channelId, request));
    }
}
