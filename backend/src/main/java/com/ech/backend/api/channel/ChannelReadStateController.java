package com.ech.backend.api.channel;

import com.ech.backend.api.channel.dto.ChannelReadStateResponse;
import com.ech.backend.api.channel.dto.UpdateChannelReadStateRequest;
import com.ech.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels/{channelId}/read-state")
public class ChannelReadStateController {

    private final ChannelReadStateService channelReadStateService;

    public ChannelReadStateController(ChannelReadStateService channelReadStateService) {
        this.channelReadStateService = channelReadStateService;
    }

    @GetMapping
    public ApiResponse<ChannelReadStateResponse> getReadState(
            @PathVariable Long channelId,
            @RequestParam String employeeNo
    ) {
        return ApiResponse.success(channelReadStateService.getReadState(channelId, employeeNo));
    }

    @PutMapping
    public ApiResponse<ChannelReadStateResponse> updateReadState(
            @PathVariable Long channelId,
            @Valid @RequestBody UpdateChannelReadStateRequest request
    ) {
        return ApiResponse.success(channelReadStateService.updateReadState(channelId, request));
    }
}
