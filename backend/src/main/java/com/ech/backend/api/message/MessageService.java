package com.ech.backend.api.message;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.message.dto.CreateMessageRequest;
import com.ech.backend.api.message.dto.MessageResponse;
import com.ech.backend.common.exception.ForbiddenException;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelMemberUserIdColumnInspector;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.message.Message;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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
    private final ChannelMemberUserIdColumnInspector legacyUserFkInspector;
    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<MessageResponse> LEGACY_MESSAGE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public MessageResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long parentId = rs.getObject("parent_message_id") == null ? null : rs.getLong("parent_message_id");
            String msgType = rs.getString("message_type");
            return new MessageResponse(
                    rs.getLong("message_id"),
                    rs.getLong("channel_id"),
                    rs.getString("sender_id"),
                    rs.getString("sender_name"),
                    parentId,
                    rs.getString("body"),
                    readCreatedAtUtc(rs),
                    msgType != null && !msgType.isBlank() ? msgType : "TEXT"
            );
        }
    };

    public MessageService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            UserRepository userRepository,
            MessageRepository messageRepository,
            AuditLogService auditLogService,
            ChannelMemberUserIdColumnInspector legacyUserFkInspector,
            JdbcTemplate jdbcTemplate
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.auditLogService = auditLogService;
        this.legacyUserFkInspector = legacyUserFkInspector;
        this.jdbcTemplate = jdbcTemplate;
    }

    private static OffsetDateTime readCreatedAtUtc(ResultSet rs) throws SQLException {
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        return ts == null ? OffsetDateTime.now(ZoneOffset.UTC) : ts.toInstant().atOffset(ZoneOffset.UTC);
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
        if (!isUserMemberOfChannel(channelId, senderEmployeeNo)) {
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

        if (legacyUserFkInspector.isLegacyMessageSenderReferencesUserPrimaryKey()) {
            return jdbcTemplate.query(
                    """
                            SELECT m.id AS message_id,
                                   m.channel_id,
                                   u.employee_no AS sender_id,
                                   u.name AS sender_name,
                                   m.parent_message_id,
                                   m.body,
                                   m.created_at,
                                   m.message_type
                            FROM messages m
                            INNER JOIN users u ON u.id = m.sender_id
                            WHERE m.parent_message_id = ?
                              AND m.archived_at IS NULL
                            ORDER BY m.created_at ASC
                            """,
                    LEGACY_MESSAGE_ROW_MAPPER,
                    parentMessageId
            );
        }
        return messageRepository.findByParentMessageIdOrderByCreatedAtAsc(parentMessageId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<MessageResponse> getChannelMessages(Long channelId, String employeeNo, int limit) {
        if (!isUserMemberOfChannel(channelId, employeeNo)) {
            throw new ForbiddenException("채널에 참여한 사용자가 아닙니다.");
        }
        if (legacyUserFkInspector.isLegacyMessageSenderReferencesUserPrimaryKey()) {
            int cap = Math.min(limit, 200);
            List<MessageResponse> rows = jdbcTemplate.query(
                    """
                            SELECT m.id AS message_id,
                                   m.channel_id,
                                   u.employee_no AS sender_id,
                                   u.name AS sender_name,
                                   m.parent_message_id,
                                   m.body,
                                   m.created_at,
                                   m.message_type
                            FROM messages m
                            INNER JOIN users u ON u.id = m.sender_id
                            WHERE m.channel_id = ?
                              AND m.parent_message_id IS NULL
                              AND m.archived_at IS NULL
                              AND m.is_deleted = false
                            ORDER BY m.created_at DESC
                            LIMIT ?
                            """,
                    LEGACY_MESSAGE_ROW_MAPPER,
                    channelId,
                    cap
            );
            Collections.reverse(rows);
            return rows;
        }
        List<Message> messages = messageRepository.findRecentByChannelId(
                channelId, PageRequest.of(0, Math.min(limit, 200)));
        Collections.reverse(messages);
        return messages.stream().map(this::toResponse).toList();
    }

    private boolean isUserMemberOfChannel(Long channelId, String employeeNo) {
        if (employeeNo == null || employeeNo.isBlank()) {
            return false;
        }
        String emp = employeeNo.trim();
        if (legacyUserFkInspector.isLegacyUserIdReferencesUserPrimaryKey()) {
            Integer n = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*)::int
                            FROM channel_members cm
                            INNER JOIN users u ON u.id = cm.user_id
                            WHERE cm.channel_id = ?
                              AND u.employee_no = ?
                            """,
                    Integer.class,
                    channelId,
                    emp
            );
            return n != null && n > 0;
        }
        return channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, emp);
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
