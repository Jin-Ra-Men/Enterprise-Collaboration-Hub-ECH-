package com.ech.backend.api.message;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.message.dto.CreateMessageRequest;
import com.ech.backend.api.message.dto.MessageResponse;
import com.ech.backend.api.message.dto.MessageTimelineItemResponse;
import com.ech.backend.api.message.dto.MessageTimelinePageResponse;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
                    msgType != null && !msgType.isBlank() ? msgType : "TEXT",
                    rs.getBoolean("sender_has_profile_image")
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
                null,
                rs.getBoolean("sender_has_profile_image")
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
                "FILE",
                null
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
            String messageType,
            Long previewSizeBytes
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
            if (previewSizeBytes != null && previewSizeBytes > 0) {
                payload.put("previewSizeBytes", previewSizeBytes);
            }
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

        boolean parentIsRoot = parent.getParentMessage() == null;

        if (legacyUserFkInspector.isLegacyMessageSenderReferencesUserPrimaryKey()) {
            String sql =
                    parentIsRoot
                            ? """
                            SELECT m.id AS message_id,
                                   m.channel_id,
                                   u.employee_no AS sender_id,
                                   u.name AS sender_name,
                                   m.parent_message_id,
                                   m.body,
                                   m.created_at,
                                   m.message_type,
                                   (u.profile_image_relpath IS NOT NULL AND TRIM(u.profile_image_relpath) <> '')
                                       AS sender_has_profile_image
                            FROM messages m
                            INNER JOIN users u ON u.id = m.sender_id
                            WHERE m.parent_message_id = ?
                              AND m.archived_at IS NULL
                              AND UPPER(COALESCE(m.message_type, '')) LIKE 'COMMENT%'
                            ORDER BY m.created_at ASC
                            """
                            : """
                            SELECT m.id AS message_id,
                                   m.channel_id,
                                   u.employee_no AS sender_id,
                                   u.name AS sender_name,
                                   m.parent_message_id,
                                   m.body,
                                   m.created_at,
                                   m.message_type,
                                   (u.profile_image_relpath IS NOT NULL AND TRIM(u.profile_image_relpath) <> '')
                                       AS sender_has_profile_image
                            FROM messages m
                            INNER JOIN users u ON u.id = m.sender_id
                            WHERE m.parent_message_id = ?
                              AND m.archived_at IS NULL
                            ORDER BY m.created_at ASC
                            """;
            return jdbcTemplate.query(sql, LEGACY_MESSAGE_ROW_MAPPER, parentMessageId);
        }
        return messageRepository.findByParentMessageIdOrderByCreatedAtAsc(parentMessageId).stream()
                .filter(
                        m ->
                                !parentIsRoot
                                        || isCommentThreadMessageType(m.getMessageType()))
                .map(this::toResponse)
                .toList();
    }

    /** 스레드 모달·댓글 수: 타임라인 답글(REPLY_*)은 제외, 댓글(COMMENT_*)만 포함 */
    private static boolean isCommentThreadMessageType(String messageType) {
        return messageType != null && messageType.toUpperCase().startsWith("COMMENT");
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
                                   m.message_type,
                                   (u.profile_image_relpath IS NOT NULL AND TRIM(u.profile_image_relpath) <> '')
                                       AS sender_has_profile_image
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

    /**
     * 타임라인 페이지. {@code beforeMessageId}가 있으면 해당 메시지보다 오래된 항목만(커서는 채널 내 타임라인 행이어야 함).
     * {@code hasMoreOlder}는 {@code limit+1}건 조회로 판별한다.
     */
    public MessageTimelinePageResponse getChannelTimelinePage(
            Long channelId, String employeeNo, int limit, Long beforeMessageId) {
        if (!isUserMemberOfChannel(channelId, employeeNo)) {
            throw new ForbiddenException("채널에 참여한 사용자가 아닙니다.");
        }

        int cap = Math.min(Math.max(limit, 1), 200);
        int fetchSize = cap + 1;

        if (legacyUserFkInspector.isLegacyMessageSenderReferencesUserPrimaryKey()) {
            return getChannelTimelinePageLegacy(channelId, cap, fetchSize, beforeMessageId);
        }

        List<Message> batch;
        if (beforeMessageId == null) {
            batch = messageRepository.findTimelineByChannelId(channelId, PageRequest.of(0, fetchSize));
        } else {
            Message cursor = messageRepository
                    .findByIdAndChannel_Id(beforeMessageId, channelId)
                    .orElseThrow(() -> new NotFoundException("메시지를 찾을 수 없습니다."));
            if (cursor.isDeleted() || cursor.isArchived()) {
                throw new NotFoundException("메시지를 찾을 수 없습니다.");
            }
            assertTimelineCursorMessage(cursor);
            batch = messageRepository.findTimelineOlderThan(
                    channelId, cursor.getCreatedAt(), cursor.getId(), PageRequest.of(0, fetchSize));
        }

        boolean hasMoreOlder = batch.size() > cap;
        List<Message> slice = hasMoreOlder ? batch.subList(0, cap) : batch;
        Collections.reverse(slice);
        List<MessageTimelineItemResponse> mapped = slice.stream().map(this::messageToTimelineItem).toList();
        return new MessageTimelinePageResponse(attachThreadCommentSummaries(mapped), hasMoreOlder);
    }

    private void assertTimelineCursorMessage(Message cursor) {
        if (cursor.getParentMessage() == null) {
            return;
        }
        String mt = cursor.getMessageType() == null ? "" : cursor.getMessageType().toUpperCase();
        if (mt.startsWith("COMMENT")) {
            throw new IllegalArgumentException("타임라인 페이지 커서로 댓글 메시지는 사용할 수 없습니다.");
        }
        if (!mt.startsWith("REPLY")) {
            throw new IllegalArgumentException("타임라인에 표시되지 않는 메시지는 커서로 사용할 수 없습니다.");
        }
    }

    private MessageTimelineItemResponse messageToTimelineItem(Message m) {
        Long parentMessageId = m.getParentMessage() == null ? null : m.getParentMessage().getId();
        boolean isReply =
                parentMessageId != null
                        && m.getMessageType() != null
                        && m.getMessageType().toUpperCase().startsWith("REPLY");

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
                    null,
                    userHasProfileImage(m.getSender()));
        }

        Message replyTo = m.getParentMessage();
        Message replyToParent = replyTo.getParentMessage();
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
                null,
                userHasProfileImage(m.getSender()));
    }

    private MessageTimelinePageResponse getChannelTimelinePageLegacy(
            long channelId, int cap, int fetchSize, Long beforeMessageId) {
        List<MessageTimelineItemResponse> rows;
        if (beforeMessageId == null) {
            rows = jdbcTemplate.query(
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
                                   pu.name AS reply_to_sender_name,
                                   (u.profile_image_relpath IS NOT NULL AND TRIM(u.profile_image_relpath) <> '')
                                       AS sender_has_profile_image
                            FROM messages m
                            INNER JOIN users u ON u.id = m.sender_id
                            LEFT JOIN messages pm ON pm.id = m.parent_message_id
                            LEFT JOIN users pu ON pu.id = pm.sender_id
                            WHERE m.channel_id = ?
                              AND m.archived_at IS NULL
                              AND m.is_deleted = false
                              AND (m.parent_message_id IS NULL OR m.message_type LIKE 'REPLY%')
                            ORDER BY m.created_at DESC, m.id DESC
                            LIMIT ?
                            """,
                    LEGACY_TIMELINE_ROW_MAPPER,
                    channelId,
                    fetchSize);
        } else {
            Message cursor = messageRepository
                    .findByIdAndChannel_Id(beforeMessageId, channelId)
                    .orElseThrow(() -> new NotFoundException("메시지를 찾을 수 없습니다."));
            if (cursor.isDeleted() || cursor.isArchived()) {
                throw new NotFoundException("메시지를 찾을 수 없습니다.");
            }
            assertTimelineCursorMessage(cursor);
            java.sql.Timestamp ts = java.sql.Timestamp.from(cursor.getCreatedAt().toInstant());
            rows = jdbcTemplate.query(
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
                                   pu.name AS reply_to_sender_name,
                                   (u.profile_image_relpath IS NOT NULL AND TRIM(u.profile_image_relpath) <> '')
                                       AS sender_has_profile_image
                            FROM messages m
                            INNER JOIN users u ON u.id = m.sender_id
                            LEFT JOIN messages pm ON pm.id = m.parent_message_id
                            LEFT JOIN users pu ON pu.id = pm.sender_id
                            WHERE m.channel_id = ?
                              AND m.archived_at IS NULL
                              AND m.is_deleted = false
                              AND (m.parent_message_id IS NULL OR m.message_type LIKE 'REPLY%')
                              AND (m.created_at < ? OR (m.created_at = ? AND m.id < ?))
                            ORDER BY m.created_at DESC, m.id DESC
                            LIMIT ?
                            """,
                    LEGACY_TIMELINE_ROW_MAPPER,
                    channelId,
                    ts,
                    ts,
                    cursor.getId(),
                    fetchSize);
        }
        boolean hasMoreOlder = rows.size() > cap;
        if (hasMoreOlder) {
            rows = new ArrayList<>(rows.subList(0, cap));
        }
        Collections.reverse(rows);
        return new MessageTimelinePageResponse(attachThreadCommentSummaries(rows), hasMoreOlder);
    }

    /**
     * 채널·DM 공통: 스레드(댓글/답글) 활동이 있는 원글만, 최근 활동 순.
     */
    public List<MessageTimelineItemResponse> getChannelThreadHubRoots(Long channelId, String employeeNo, int limit) {
        if (!isUserMemberOfChannel(channelId, employeeNo)) {
            throw new ForbiddenException("채널에 참여한 사용자가 아닙니다.");
        }
        int cap = Math.min(Math.max(limit, 1), 100);
        List<Long> orderedIds = messageRepository.findThreadRootIdsByChannelOrderByLastActivity(channelId, cap);
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        List<Message> loaded = messageRepository.findAllById(orderedIds);
        Map<Long, Message> byId = new HashMap<>();
        for (Message m : loaded) {
            if (m.getParentMessage() != null) {
                continue;
            }
            if (m.getChannel() == null || !m.getChannel().getId().equals(channelId)) {
                continue;
            }
            if (m.isDeleted() || m.isArchived()) {
                continue;
            }
            byId.put(m.getId(), m);
        }
        List<MessageTimelineItemResponse> placeholders = new ArrayList<>();
        for (Long id : orderedIds) {
            Message m = byId.get(id);
            if (m == null) {
                continue;
            }
            placeholders.add(
                    new MessageTimelineItemResponse(
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
                            null,
                            userHasProfileImage(m.getSender())));
        }
        return attachThreadCommentSummaries(placeholders);
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
                            lastSender,
                            i.senderHasProfileImage()
                    );
                })
                .toList();
    }

    private Map<Long, ThreadCommentAgg> aggregateThreadCommentsForRoots(List<Long> rootIds) {
        if (rootIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> rootIdSet = new HashSet<>(rootIds);
        List<Message> comments = messageRepository.findThreadActivityUnderRoots(rootIds);
        Map<Long, ThreadCommentAgg> map = new HashMap<>();
        Set<Long> seenIds = new HashSet<>();
        for (Message cm : comments) {
            if (cm.getParentMessage() == null) {
                continue;
            }
            Long mid = cm.getId();
            if (mid != null && !seenIds.add(mid)) {
                continue;
            }
            Long rootId = resolveThreadRootMessageId(cm);
            if (rootId == null || !rootIdSet.contains(rootId)) {
                continue;
            }
            if (!isCommentThreadMessageType(cm.getMessageType())) {
                continue;
            }
            map.computeIfAbsent(rootId, k -> new ThreadCommentAgg()).accept(cm);
        }
        return map;
    }

    /**
     * 스레드 내 임의 메시지(댓글·답글)에서 타임라인에 보이는 루트 메시지 id를 구한다.
     * 직접 부모만 쓰면 댓글에 달린 답글이 루트가 아닌 부모 id로 집계되어 루트 건수가 틀어질 수 있다.
     */
    private static Long resolveThreadRootMessageId(Message m) {
        Message cur = m;
        while (cur.getParentMessage() != null) {
            cur = cur.getParentMessage();
        }
        return cur.getId();
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
                message.getMessageType(),
                userHasProfileImage(message.getSender())
        );
    }

    private static boolean userHasProfileImage(User u) {
        if (u == null) {
            return false;
        }
        String p = u.getProfileImageRelPath();
        return p != null && !p.isBlank();
    }
}
