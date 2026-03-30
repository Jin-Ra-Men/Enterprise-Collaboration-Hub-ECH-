package com.ech.backend.api.message;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.message.dto.CreateMessageRequest;
import com.ech.backend.api.message.dto.MessageResponse;
import com.ech.backend.common.exception.ForbiddenException;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.message.Message;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MessageService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final AuditLogService auditLogService;

    public MessageService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            UserRepository userRepository,
            MessageRepository messageRepository,
            AuditLogService auditLogService
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public MessageResponse createMessage(Long channelId, CreateMessageRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User sender = userRepository.findByEmployeeNo(request.senderId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Message message = messageRepository.save(new Message(channel, sender, null, request.text()));

        auditLogService.safeRecord(
                AuditEventType.MESSAGE_SENT,
                sender.getId(),
                "MESSAGE",
                message.getId(),
                channel.getWorkspaceKey(),
                "channelId=" + channelId,
                null
        );

        return toResponse(message);
    }

    /**
     * 채널에 파일 첨부를 일반 메시지 목록에 남긴다(새로고침 후에도 동일하게 보이도록).
     */
    @Transactional
    public MessageResponse createFileAttachmentMessage(
            Long channelId,
            String senderEmployeeNo,
            Long fileId,
            String originalFilename,
            long sizeBytes,
            String contentType
    ) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User sender = userRepository.findByEmployeeNo(senderEmployeeNo)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, senderEmployeeNo)) {
            throw new ForbiddenException("채널에 참여한 사용자가 아닙니다.");
        }
        String body;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("kind", "FILE");
            payload.put("fileId", fileId);
            payload.put("originalFilename", originalFilename != null ? originalFilename : "");
            payload.put("sizeBytes", sizeBytes);
            payload.put("contentType", contentType != null && !contentType.isBlank() ? contentType : "");
            body = OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("파일 메시지 본문 직렬화 실패", e);
        }

        Message message = messageRepository.save(new Message(channel, sender, null, body, "FILE"));

        auditLogService.safeRecord(
                AuditEventType.MESSAGE_SENT,
                sender.getId(),
                "MESSAGE",
                message.getId(),
                channel.getWorkspaceKey(),
                "channelId=" + channelId + " type=FILE fileId=" + fileId,
                null
        );

        return toResponse(message);
    }

    @Transactional
    public MessageResponse createReply(Long channelId, Long parentMessageId, CreateMessageRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User sender = userRepository.findByEmployeeNo(request.senderId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Message parent = messageRepository.findById(parentMessageId)
                .orElseThrow(() -> new IllegalArgumentException("부모 메시지를 찾을 수 없습니다."));

        if (!parent.getChannel().getId().equals(channelId)) {
            throw new IllegalArgumentException("부모 메시지의 채널이 일치하지 않습니다.");
        }

        Message reply = messageRepository.save(new Message(channel, sender, parent, request.text()));

        auditLogService.safeRecord(
                AuditEventType.MESSAGE_REPLY_SENT,
                sender.getId(),
                "MESSAGE",
                reply.getId(),
                channel.getWorkspaceKey(),
                "channelId=" + channelId + " parentId=" + parentMessageId,
                null
        );

        return toResponse(reply);
    }

    public List<MessageResponse> getThreadReplies(Long channelId, Long parentMessageId) {
        Message parent = messageRepository.findById(parentMessageId)
                .orElseThrow(() -> new IllegalArgumentException("부모 메시지를 찾을 수 없습니다."));
        if (!parent.getChannel().getId().equals(channelId)) {
            throw new IllegalArgumentException("부모 메시지의 채널이 일치하지 않습니다.");
        }

        return messageRepository.findByParentMessageIdOrderByCreatedAtAsc(parentMessageId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<MessageResponse> getChannelMessages(Long channelId, String employeeNo, int limit) {
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, employeeNo)) {
            throw new ForbiddenException("채널에 참여한 사용자가 아닙니다.");
        }
        List<Message> messages = messageRepository.findRecentByChannelId(
                channelId, PageRequest.of(0, Math.min(limit, 200)));
        Collections.reverse(messages);
        return messages.stream().map(this::toResponse).toList();
    }

    private MessageResponse toResponse(Message message) {
        Long parentMessageId = message.getParentMessage() == null ? null : message.getParentMessage().getId();
        return new MessageResponse(
                message.getId(),
                message.getChannel().getId(),
                message.getSender().getEmployeeNo(),
                message.getSender().getName(),
                parentMessageId,
                message.getBody(),
                message.getCreatedAt(),
                message.getMessageType()
        );
    }
}
