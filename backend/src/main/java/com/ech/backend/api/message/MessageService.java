package com.ech.backend.api.message;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.message.dto.CreateMessageRequest;
import com.ech.backend.api.message.dto.MessageTimelineItemResponse;
import com.ech.backend.api.message.dto.MessageResponse;
import com.ech.backend.common.mention.MentionParser;
import com.ech.backend.common.exception.ForbiddenException;
import com.ech.backend.common.exception.NotFoundException;
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
import java.util.Objects;
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
    private final MentionNotificationService mentionNotificationService;

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

    private static final RowMapper<MessageTimelineItemResponse> LEGACY_TIMELINE_ROW_MAPPER = (rs, rowNum) -> {
        Long parentId = rs.getObject("parent_message_id") == null ? null : rs.getLong("parent_message_id");
        Long replyToMessageId = rs.getObject("reply_to_message_id") == null ? null : rs.getLong("reply_to_message_id");
        Long replyToParentMessageId = rs.getObject("reply_to_parent_message_id") == null ? null : rs.getLong("reply_to_parent_message_id");
        String msgType = rs.getString("message_type");

        boolean isReply = parentId != null;
        String replyToKind = null;
        Long replyToRootMessageId = null;
        String replyToPreview = null;
        if (isReply && replyToMessageId != null) {
            replyToKind = replyToParentMessageId == null ? "ROOT" : "COMMENT";
            replyToRootMessageId = replyToParentMessageId == null ? replyToMessageId : replyToParentMessageId;
            String replyToBody = rs.getString("reply_to_body");
            replyToPreview = previewForReplyTargetBody(replyToBody);
        }

        String replyToSenderName = isReply ? rs.getString("reply_to_sender_name") : null;

        Integer threadCommentCount = isReply ? null : 0;
        return new MessageTimelineItemResponse(
                rs.getLong("message_id"),
                rs.getLong("channel_id"),
                rs.getString("sender_id"),
                rs.getString("sender_name"),
                parentId,
                rs.getString("body"),
                readCreatedAtUtc(rs),
                msgType != null && !msgType.isBlank() ? msgType : "TEXT",
                isReply,
                replyToMessageId,
                replyToKind,
                replyToRootMessageId,
                replyToPreview,
                replyToSenderName,
                threadCommentCount,
                null,
                null
        );
    };

    private static String previewForReplyTargetBody(String body) {
        if (body == null) return "";
        String s = body.trim();
        // FILE 메시지 본문은 JSON이며 kind=FILE을 포함한다.
        if (s.startsWith("{") && s.contains("\"kind\"") && s.contains("FILE")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> o = OBJECT_MAPPER.readValue(s, Map.class);
                if (o != null && "FILE".equals(String.valueOf(o.get("kind"))) && o.get("originalFilename") != null) {
                    String fn = String.valueOf(o.get("originalFilename")).trim();
                    if (!fn.isEmpty()) return fn;
                }
            } catch (Exception ignored) {
                // fall back to text preview
            }
        }
        return MentionParser.previewForToast(body, 80);
    }

    public MessageService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            UserRepository userRepository,
            MessageRepository messageRepository,
            AuditLogService auditLogService,
            ChannelMemberUserIdColumnInspector legacyUserFkInspector,
            JdbcTemplate jdbcTemplate,
            MentionNotificationService mentionNotificationService
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.auditLogService = auditLogService;
        this.legacyUserFkInspector = legacyUserFkInspector;
        this.jdbcTemplate = jdbcTemplate;
        this.mentionNotificationService = mentionNotificationService;
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

        mentionNotificationService.dispatchForNewMessage(channel, message, sender);
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
        // 기존 업로드는 루트 메시지로 간주한다(댓글/답글 붙이기는 오버로드에서 처리).
        return createFileAttachmentMessage(
                channelId,
                senderEmployeeNo,
                null,
                fileId,
                originalFilename,
                sizeBytes,
                contentType,
                "FILE"
        );
    }

    @Transactional
    public MessageResponse createFileAttachmentMessage(
            Long channelId,
            String senderEmployeeNo,
            Long parentMessageId,
            Long fileId,
            String originalFilename,
            long sizeBytes,
            String contentType,
            String messageType
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

        Message parent = null;
        if (parentMessageId != null) {
            parent = messageRepository.findById(parentMessageId)
                    .orElseThrow(() -> new IllegalArgumentException("부모 메시지를 찾을 수 없습니다."));
            if (!parent.getChannel().getId().equals(channelId)) {
                throw new IllegalArgumentException("부모 메시지의 채널이 일치하지 않습니다.");
            }
        }

        String mt = messageType != null && !messageType.isBlank() ? messageType : "FILE";
        Message message = messageRepository.save(new Message(channel, sender, parent, body, mt));

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

        // REPLY는 첨부(payload)는 별도로(예: FILE) 처리하되, 스레드 종류는 messageType 접두사로 구분한다.
        Message reply = messageRepository.save(new Message(channel, sender, parent, request.text(), "REPLY_TEXT"));

        auditLogService.safeRecord(
                AuditEventType.MESSAGE_REPLY_SENT,
                sender.getId(),
                "MESSAGE",
                reply.getId(),
                channel.getWorkspaceKey(),
                "channelId=" + channelId + " parentId=" + parentMessageId,
                null
        );

        mentionNotificationService.dispatchForNewMessage(channel, reply, sender);
        return toResponse(reply);
    }

    @Transactional
    public MessageResponse createComment(Long channelId, Long parentMessageId, CreateMessageRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User sender = userRepository.findByEmployeeNo(request.senderId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Message parent = messageRepository.findById(parentMessageId)
                .orElseThrow(() -> new IllegalArgumentException("부모 메시지를 찾을 수 없습니다."));

        if (!parent.getChannel().getId().equals(channelId)) {
            throw new IllegalArgumentException("부모 메시지의 채널이 일치하지 않습니다.");
        }

        // COMMENT는 기본 텍스트 댓글(첨부는 FILE 계열로 확장될 예정). 종류 구분용으로 messageType을 COMMENT_TEXT로 저장한다.
        Message comment = messageRepository.save(new Message(channel, sender, parent, request.text(), "COMMENT_TEXT"));

        auditLogService.safeRecord(
                AuditEventType.MESSAGE_SENT,
                sender.getId(),
                "MESSAGE",
                comment.getId(),
                channel.getWorkspaceKey(),
                "channelId=" + channelId + " parentId=" + parentMessageId,
                null
        );

        mentionNotificationService.dispatchForNewMessage(channel, comment, sender);
        return toResponse(comment);
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

    /**
     * 채널 내 단건 메시지 조회(멤버십 검증, 삭제·보관 제외).
     * 스레드 모달에서 타임라인 캐시에 없는 원글을 불러올 때 사용한다.
     */
    public MessageResponse getChannelMessage(Long channelId, Long messageId, String employeeNo) {
        if (!isUserMemberOfChannel(channelId, employeeNo)) {
            throw new ForbiddenException("채널에 참여한 사용자가 아닙니다.");
        }
        Message message = messageRepository.findByIdAndChannel_Id(messageId, channelId)
                .orElseThrow(() -> new NotFoundException("메시지를 찾을 수 없습니다."));
        if (message.isDeleted() || message.isArchived()) {
            throw new NotFoundException("메시지를 찾을 수 없습니다.");
        }
        return toResponse(message);
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

    public List<MessageTimelineItemResponse> getChannelTimelineMessages(Long channelId, String employeeNo, int limit) {
        if (!isUserMemberOfChannel(channelId, employeeNo)) {
            throw new ForbiddenException("채널에 참여한 사용자가 아닙니다.");
        }

        int cap = Math.min(limit, 200);
        if (legacyUserFkInspector.isLegacyMessageSenderReferencesUserPrimaryKey()) {
            List<MessageTimelineItemResponse> rows = jdbcTemplate.query(
                    """
                            SELECT m.id AS message_id,
                                   m.channel_id,
                                   u.employee_no AS sender_id,
                                   u.name AS sender_name,
                                   m.parent_message_id,
                                   m.body,
                                   m.created_at,
                                   m.message_type,
                                   pm.id AS reply_to_message_id,
                                   pm.parent_message_id AS reply_to_parent_message_id,
                                   pm.body AS reply_to_body,
                                   pu.name AS reply_to_sender_name
                            FROM messages m
                            INNER JOIN users u ON u.id = m.sender_id
                            LEFT JOIN messages pm ON pm.id = m.parent_message_id
                            LEFT JOIN users pu ON pu.id = pm.sender_id
                            WHERE m.channel_id = ?
                              AND m.archived_at IS NULL
                              AND m.is_deleted = false
                              AND (m.parent_message_id IS NULL OR m.message_type LIKE 'REPLY%')
                            ORDER BY m.created_at DESC
                            LIMIT ?
                            """,
                    LEGACY_TIMELINE_ROW_MAPPER,
                    channelId,
                    cap
            );
            Collections.reverse(rows);
            return attachThreadCommentSummaries(rows);
        }

        List<Message> messages = messageRepository.findTimelineByChannelId(
                channelId,
                PageRequest.of(0, cap)
        );
        Collections.reverse(messages);
        List<MessageTimelineItemResponse> mapped = messages.stream().map(m -> {
            Long parentMessageId = m.getParentMessage() == null ? null : m.getParentMessage().getId();
            boolean isReply = parentMessageId != null && m.getMessageType() != null && m.getMessageType().toUpperCase().startsWith("REPLY");

            if (!isReply) {
                return new MessageTimelineItemResponse(
                        m.getId(),
                        m.getChannel().getId(),
                        m.getSender().getEmployeeNo(),
                        m.getSender().getName(),
                        null,
                        m.getBody(),
                        m.getCreatedAt(),
                        m.getMessageType(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        null,
                        null
                );
            }

            Message replyTo = m.getParentMessage();
            Message replyToParent = replyTo.getParentMessage(); // null이면 ROOT
            Long replyToRootMessageId = replyToParent == null ? replyTo.getId() : replyToParent.getId();
            String replyToKind = replyToParent == null ? "ROOT" : "COMMENT";
            String replyTargetAuthor = replyTo.getSender() != null ? replyTo.getSender().getName() : null;

            return new MessageTimelineItemResponse(
                    m.getId(),
                    m.getChannel().getId(),
                    m.getSender().getEmployeeNo(),
                    m.getSender().getName(),
                    replyTo.getId(),
                    m.getBody(),
                    m.getCreatedAt(),
                    m.getMessageType(),
                    true,
                    replyTo.getId(),
                    replyToKind,
                    replyToRootMessageId,
                    previewForReplyTargetBody(replyTo.getBody()),
                    replyTargetAuthor,
                    null,
                    null,
                    null
            );
        }).toList();
        return attachThreadCommentSummaries(mapped);
    }

    private List<MessageTimelineItemResponse> attachThreadCommentSummaries(List<MessageTimelineItemResponse> items) {
        List<Long> rootIds = items.stream()
                .filter(i -> !i.isReply())
                .map(MessageTimelineItemResponse::messageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ThreadCommentAgg> aggByRoot = aggregateThreadCommentsForRoots(rootIds);
        return items.stream()
                .map(i -> {
                    if (i.isReply()) {
                        return i;
                    }
                    ThreadCommentAgg a = aggByRoot.get(i.messageId());
                    int c = a == null ? 0 : a.count;
                    OffsetDateTime lastAt = c > 0 && a != null ? a.lastAt : null;
                    String lastSender = c > 0 && a != null ? a.lastSenderName : null;
                    return new MessageTimelineItemResponse(
                            i.messageId(),
                            i.channelId(),
                            i.senderId(),
                            i.senderName(),
                            i.parentMessageId(),
                            i.text(),
                            i.createdAt(),
                            i.messageType(),
                            false,
                            i.replyToMessageId(),
                            i.replyToKind(),
                            i.replyToRootMessageId(),
                            i.replyToPreview(),
                            i.replyToSenderName(),
                            c,
                            lastAt,
                            lastSender
                    );
                })
                .toList();
    }

    private Map<Long, ThreadCommentAgg> aggregateThreadCommentsForRoots(List<Long> rootIds) {
        if (rootIds.isEmpty()) {
            return Map.of();
        }
        List<Message> comments = messageRepository.findThreadActivityUnderRoots(rootIds);
        Map<Long, ThreadCommentAgg> map = new HashMap<>();
        for (Message cm : comments) {
            if (cm.getParentMessage() == null) {
                continue;
            }
            Long rid = cm.getParentMessage().getId();
            map.computeIfAbsent(rid, k -> new ThreadCommentAgg()).accept(cm);
        }
        return map;
    }

    private static final class ThreadCommentAgg {
        private int count;
        private OffsetDateTime lastAt;
        private String lastSenderName;

        void accept(Message cm) {
            count++;
            OffsetDateTime t = cm.getCreatedAt();
            if (lastAt == null || t.isAfter(lastAt)) {
                lastAt = t;
                lastSenderName = cm.getSender() != null ? cm.getSender().getName() : null;
            }
        }
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
