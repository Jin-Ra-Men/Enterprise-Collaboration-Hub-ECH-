package com.ech.backend.api.work;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.work.dto.CreateWorkItemFromMessageRequest;
import com.ech.backend.api.work.dto.CreateWorkItemRequest;
import com.ech.backend.api.work.dto.UpdateWorkItemRequest;
import com.ech.backend.api.work.dto.WorkItemResponse;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.message.Message;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import com.ech.backend.domain.work.WorkItem;
import com.ech.backend.domain.work.WorkItemRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkItemService {

    private static final int DEFAULT_TITLE_MAX = 80;
    private static final int DEFAULT_DESCRIPTION_MAX = 4000;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelRepository channelRepository;
    private final WorkItemRepository workItemRepository;
    private final AuditLogService auditLogService;

    public WorkItemService(
            MessageRepository messageRepository,
            UserRepository userRepository,
            ChannelMemberRepository channelMemberRepository,
            ChannelRepository channelRepository,
            WorkItemRepository workItemRepository,
            AuditLogService auditLogService
    ) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.channelRepository = channelRepository;
        this.workItemRepository = workItemRepository;
        this.auditLogService = auditLogService;
    }

    public WorkItemResponse getById(Long workItemId) {
        WorkItem item = workItemRepository.findById(workItemId)
                .orElseThrow(() -> new IllegalArgumentException("업무 항목을 찾을 수 없습니다."));
        return toResponse(item);
    }

    @Transactional
    public WorkItemResponse createFromMessage(Long messageId, CreateWorkItemFromMessageRequest request) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));
        User creator = userRepository.findByEmployeeNo(request.createdByEmployeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Long channelId = message.getChannel().getId();
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, request.createdByEmployeeNo())) {
            throw new IllegalArgumentException("채널 멤버만 메시지에서 업무를 생성할 수 있습니다.");
        }
        if (workItemRepository.findBySourceMessage_Id(messageId).isPresent()) {
            throw new IllegalArgumentException("이 메시지에 이미 연결된 업무 항목이 있습니다.");
        }

        String title = buildTitle(request.title(), message.getBody());
        String description = buildDescription(request.description(), message.getBody());
        String status = request.status() == null || request.status().isBlank()
                ? "OPEN"
                : request.status().trim();

        WorkItem saved = workItemRepository.save(new WorkItem(
                title,
                description,
                status,
                message,
                message.getChannel(),
                creator
        ));

        auditLogService.safeRecord(
                AuditEventType.WORK_ITEM_CREATED,
                creator.getId(),
                "WORK_ITEM",
                saved.getId(),
                null,
                "sourceMessageId=" + messageId + " channelId=" + channelId,
                null
        );

        return toResponse(saved);
    }

    public List<WorkItemResponse> listByMessageId(Long messageId) {
        messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));
        return workItemRepository.findBySourceMessage_Id(messageId)
                .map(w -> List.of(toResponse(w)))
                .orElseGet(List::of);
    }

    public List<WorkItemResponse> listByChannelId(Long channelId, String employeeNo, int limit) {
        String actorEmployeeNo = employeeNo == null ? "" : employeeNo.trim();
        if (actorEmployeeNo.isBlank()) {
            throw new IllegalArgumentException("employeeNo는 필수입니다.");
        }
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, actorEmployeeNo)) {
            throw new IllegalArgumentException("채널 멤버만 업무 항목을 조회할 수 있습니다.");
        }
        int size = Math.min(Math.max(limit, 1), 100);
        return workItemRepository.findBySourceChannel_IdOrderByCreatedAtDesc(
                        channelId,
                        org.springframework.data.domain.PageRequest.of(0, size))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WorkItemResponse createInChannel(Long channelId, CreateWorkItemRequest request) {
        String creatorEmployeeNo = request.createdByEmployeeNo() == null ? "" : request.createdByEmployeeNo().trim();
        if (creatorEmployeeNo.isBlank()) {
            throw new IllegalArgumentException("createdByEmployeeNo는 필수입니다.");
        }
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, creatorEmployeeNo)) {
            throw new IllegalArgumentException("채널 멤버만 업무 항목을 생성할 수 있습니다.");
        }
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User creator = userRepository.findByEmployeeNo(creatorEmployeeNo)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Message sourceMessage = null;
        if (request.sourceMessageId() != null) {
            sourceMessage = messageRepository.findById(request.sourceMessageId())
                    .orElseThrow(() -> new IllegalArgumentException("원본 메시지를 찾을 수 없습니다."));
            if (!sourceMessage.getChannel().getId().equals(channelId)) {
                throw new IllegalArgumentException("원본 메시지의 채널이 일치하지 않습니다.");
            }
        }
        String status = request.status() == null || request.status().isBlank() ? "OPEN" : request.status().trim();
        WorkItem saved = workItemRepository.save(new WorkItem(
                request.title().trim(),
                request.description(),
                status,
                sourceMessage,
                channel,
                creator
        ));
        auditLogService.safeRecord(
                AuditEventType.WORK_ITEM_CREATED,
                creator.getId(),
                "WORK_ITEM",
                saved.getId(),
                channel.getWorkspaceKey(),
                "channelId=" + channelId,
                null
        );
        return toResponse(saved);
    }

    @Transactional
    public WorkItemResponse updateWorkItem(Long workItemId, UpdateWorkItemRequest request) {
        String actorEmployeeNo = request.actorEmployeeNo() == null ? "" : request.actorEmployeeNo().trim();
        if (actorEmployeeNo.isBlank()) {
            throw new IllegalArgumentException("actorEmployeeNo는 필수입니다.");
        }
        WorkItem item = workItemRepository.findById(workItemId)
                .orElseThrow(() -> new IllegalArgumentException("업무 항목을 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(item.getSourceChannel().getId(), actorEmployeeNo)) {
            throw new IllegalArgumentException("채널 멤버만 업무 항목을 수정할 수 있습니다.");
        }
        item.update(request.title(), request.description(), request.status());
        WorkItem saved = workItemRepository.save(item);
        return toResponse(saved);
    }

    @Transactional
    public void deleteWorkItem(Long workItemId, String actorEmployeeNo) {
        String actor = actorEmployeeNo == null ? "" : actorEmployeeNo.trim();
        if (actor.isBlank()) {
            throw new IllegalArgumentException("actorEmployeeNo는 필수입니다.");
        }
        WorkItem item = workItemRepository.findById(workItemId)
                .orElseThrow(() -> new IllegalArgumentException("업무 항목을 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(item.getSourceChannel().getId(), actor)) {
            throw new IllegalArgumentException("채널 멤버만 업무 항목을 삭제할 수 있습니다.");
        }
        workItemRepository.delete(item);
    }

    private static String buildTitle(String requestedTitle, String messageBody) {
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            String t = requestedTitle.trim();
            return t.length() > 500 ? t.substring(0, 500) : t;
        }
        String body = messageBody == null ? "" : messageBody.trim();
        if (body.isEmpty()) {
            return "(제목 없음)";
        }
        String oneLine = body.split("\\R", 2)[0].trim();
        return oneLine.length() > DEFAULT_TITLE_MAX ? oneLine.substring(0, DEFAULT_TITLE_MAX) : oneLine;
    }

    private static String buildDescription(String requestedDescription, String messageBody) {
        if (requestedDescription != null && !requestedDescription.isBlank()) {
            return truncate(requestedDescription.trim(), 8000);
        }
        if (messageBody == null || messageBody.isBlank()) {
            return null;
        }
        return truncate(messageBody.trim(), DEFAULT_DESCRIPTION_MAX);
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private WorkItemResponse toResponse(WorkItem item) {
        Long messageId = item.getSourceMessage() == null ? null : item.getSourceMessage().getId();
        return new WorkItemResponse(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getStatus(),
                messageId,
                item.getSourceChannel().getId(),
                item.getCreatedBy().getEmployeeNo(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
