package com.ech.backend.api.message;

import com.ech.backend.api.message.dto.CreateMessageRequest;
import com.ech.backend.api.message.dto.MessageResponse;
import com.ech.backend.common.api.ApiResponse;
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
@RequestMapping("/api/channels/{channelId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResponse<List<MessageResponse>> getMessages(
            @PathVariable Long channelId,
            @RequestParam String employeeNo,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.success(messageService.getChannelMessages(channelId, employeeNo, limit));
    }

    @PostMapping
    public ApiResponse<MessageResponse> createMessage(
            @PathVariable Long channelId,
            @Valid @RequestBody CreateMessageRequest request
    ) {
        return ApiResponse.success(messageService.createMessage(channelId, request));
    }

    @PostMapping("/{parentMessageId}/replies")
    public ApiResponse<MessageResponse> createReply(
            @PathVariable Long channelId,
            @PathVariable Long parentMessageId,
            @Valid @RequestBody CreateMessageRequest request
    ) {
        return ApiResponse.success(messageService.createReply(channelId, parentMessageId, request));
    }

    @GetMapping("/{parentMessageId}/replies")
    public ApiResponse<List<MessageResponse>> getThreadReplies(
            @PathVariable Long channelId,
            @PathVariable Long parentMessageId
    ) {
        return ApiResponse.success(messageService.getThreadReplies(channelId, parentMessageId));
    }
}
