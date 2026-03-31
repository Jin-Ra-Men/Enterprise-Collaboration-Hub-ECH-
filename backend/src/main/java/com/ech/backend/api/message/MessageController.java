package com.ech.backend.api.message;

import com.ech.backend.api.message.dto.CreateMessageRequest;
import com.ech.backend.api.message.dto.MessageResponse;
import com.ech.backend.api.message.dto.MessageTimelineItemResponse;
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

    @PostMapping("/{parentMessageId}/comments")
    public ApiResponse<MessageResponse> createComment(
            @PathVariable Long channelId,
            @PathVariable Long parentMessageId,
            @Valid @RequestBody CreateMessageRequest request
    ) {
        return ApiResponse.success(messageService.createComment(channelId, parentMessageId, request));
    }

    /**
     * 리터럴 경로는 가변 세그먼트({parentMessageId}/replies)보다 먼저 두어,
     * 일부 환경에서 {@code /messages/timeline}이 핸들러 미매칭(404)으로 떨어지는 것을 방지한다.
     */
    @GetMapping("/timeline")
    public ApiResponse<List<MessageTimelineItemResponse>> getTimelineMessages(
            @PathVariable Long channelId,
            @RequestParam String employeeNo,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.success(messageService.getChannelTimelineMessages(channelId, employeeNo, limit));
    }

    /**
     * 채널 내 메시지 단건(타임라인 범위 밖 원글 로드 등). 경로는 리터럴 {@code /timeline} 다음,
     * {@code /{id}/replies}보다 짧은 한 세그먼트만 매칭된다.
     */
    @GetMapping("/{messageId}")
    public ApiResponse<MessageResponse> getChannelMessage(
            @PathVariable Long channelId,
            @PathVariable Long messageId,
            @RequestParam String employeeNo
    ) {
        return ApiResponse.success(messageService.getChannelMessage(channelId, messageId, employeeNo));
    }

    @GetMapping("/{parentMessageId}/replies")
    public ApiResponse<List<MessageResponse>> getThreadReplies(
            @PathVariable Long channelId,
            @PathVariable Long parentMessageId
    ) {
        return ApiResponse.success(messageService.getThreadReplies(channelId, parentMessageId));
    }
}
