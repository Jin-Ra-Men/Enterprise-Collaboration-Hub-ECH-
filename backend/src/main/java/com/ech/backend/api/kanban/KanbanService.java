package com.ech.backend.api.kanban;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.kanban.dto.CreateKanbanBoardRequest;
import com.ech.backend.api.kanban.dto.CreateKanbanCardRequest;
import com.ech.backend.api.kanban.dto.CreateKanbanColumnRequest;
import com.ech.backend.api.kanban.dto.KanbanAssigneeMutationRequest;
import com.ech.backend.api.kanban.dto.KanbanBoardDetailResponse;
import com.ech.backend.api.kanban.dto.KanbanBoardSummaryResponse;
import com.ech.backend.api.kanban.dto.KanbanCardEventResponse;
import com.ech.backend.api.kanban.dto.KanbanCardResponse;
import com.ech.backend.api.kanban.dto.KanbanColumnResponse;
import com.ech.backend.api.kanban.dto.UpdateKanbanCardRequest;
import com.ech.backend.api.kanban.dto.UpdateKanbanColumnRequest;
import com.ech.backend.domain.kanban.KanbanBoard;
import com.ech.backend.domain.kanban.KanbanBoardRepository;
import com.ech.backend.domain.kanban.KanbanCard;
import com.ech.backend.domain.kanban.KanbanCardAssignee;
import com.ech.backend.domain.kanban.KanbanCardAssigneeRepository;
import com.ech.backend.domain.kanban.KanbanCardEvent;
import com.ech.backend.domain.kanban.KanbanCardEventRepository;
import com.ech.backend.domain.kanban.KanbanCardEventType;
import com.ech.backend.domain.kanban.KanbanCardRepository;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.kanban.KanbanColumn;
import com.ech.backend.domain.kanban.KanbanColumnRepository;
import com.ech.backend.common.exception.ForbiddenException;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class KanbanService {

    private static final int MAX_BOARDS_LIST = 100;
    private static final int MAX_HISTORY = 100;

    private final KanbanBoardRepository boardRepository;
    private final KanbanColumnRepository columnRepository;
    private final KanbanCardRepository cardRepository;
    private final KanbanCardAssigneeRepository assigneeRepository;
    private final KanbanCardEventRepository eventRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;

    public KanbanService(
            KanbanBoardRepository boardRepository,
            KanbanColumnRepository columnRepository,
            KanbanCardRepository cardRepository,
            KanbanCardAssigneeRepository assigneeRepository,
            KanbanCardEventRepository eventRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository
    ) {
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.cardRepository = cardRepository;
        this.assigneeRepository = assigneeRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
    }

    @Transactional
    public KanbanBoardSummaryResponse createBoard(CreateKanbanBoardRequest request) {
        if (boardRepository.findByWorkspaceKeyAndName(request.workspaceKey(), request.name()).isPresent()) {
            throw new IllegalArgumentException("동일 워크스페이스에 같은 이름의 보드가 이미 있습니다.");
        }
        User creator = userRepository.findByEmployeeNo(request.createdByEmployeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        KanbanBoard board = boardRepository.save(new KanbanBoard(
                request.workspaceKey().trim(),
                request.name().trim(),
                request.description() == null ? null : request.description().trim(),
                creator
        ));
        auditLogService.safeRecord(
                AuditEventType.KANBAN_BOARD_CREATED,
                creator.getId(),
                "KANBAN_BOARD",
                board.getId(),
                board.getWorkspaceKey(),
                "name=" + board.getName(),
                null
        );
        return toSummary(board);
    }

    public List<KanbanBoardSummaryResponse> listBoards(String workspaceKey) {
        String key = workspaceKey == null || workspaceKey.isBlank() ? "default" : workspaceKey.trim();
        return boardRepository
                .findByWorkspaceKeyOrderByCreatedAtDesc(key, PageRequest.of(0, MAX_BOARDS_LIST))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    public KanbanBoardDetailResponse getBoard(Long boardId) {
        KanbanBoard board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("보드를 찾을 수 없습니다."));
        List<KanbanColumn> columns = columnRepository.findByBoard_IdOrderBySortOrderAsc(boardId);
        List<KanbanCard> cards = cardRepository.findAllForBoardWithAssignees(boardId);
        Map<Long, List<KanbanCard>> cardsByColumn = new LinkedHashMap<>();
        for (KanbanColumn col : columns) {
            cardsByColumn.put(col.getId(), new ArrayList<>());
        }
        for (KanbanCard card : cards) {
            List<KanbanCard> bucket = cardsByColumn.get(card.getColumn().getId());
            if (bucket != null) {
                bucket.add(card);
            }
        }
        cardsByColumn.values().forEach(bucket -> bucket.sort(Comparator.comparingInt(KanbanCard::getSortOrder)));
        List<KanbanColumnResponse> columnResponses = columns.stream()
                .map(col -> new KanbanColumnResponse(
                        col.getId(),
                        col.getName(),
                        col.getSortOrder(),
                        cardsByColumn.getOrDefault(col.getId(), List.of()).stream()
                                .map(this::toCardResponse)
                                .toList(),
                        col.getCreatedAt()
                ))
                .toList();
        return new KanbanBoardDetailResponse(
                board.getId(),
                board.getWorkspaceKey(),
                board.getName(),
                board.getDescription(),
                board.getCreatedBy().getEmployeeNo(),
                board.getCreatedAt(),
                board.getUpdatedAt(),
                columnResponses
        );
    }

    @Transactional
    public KanbanBoardDetailResponse getOrCreateChannelBoard(Long channelId, String employeeNo) {
        String actorEmployeeNo = employeeNo == null ? "" : employeeNo.trim();
        if (actorEmployeeNo.isBlank()) {
            throw new IllegalArgumentException("employeeNo는 필수입니다.");
        }
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, actorEmployeeNo)) {
            throw new IllegalArgumentException("채널 멤버만 칸반 보드를 조회할 수 있습니다.");
        }
        String workspaceKey = channel.getWorkspaceKey();
        KanbanBoard board = boardRepository.findByWorkspaceKeyAndSourceChannel_Id(workspaceKey, channelId)
                .orElseGet(() -> createDefaultChannelBoard(channel, actorEmployeeNo));
        return getBoard(board.getId());
    }

    private KanbanBoard createDefaultChannelBoard(Channel channel, String actorEmployeeNo) {
        User creator = userRepository.findByEmployeeNo(actorEmployeeNo)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        String boardName = (channel.getName() == null || channel.getName().isBlank())
                ? "채널 " + channel.getId() + " 보드"
                : channel.getName().trim() + " 보드";
        if (boardName.length() > 200) {
            boardName = boardName.substring(0, 200);
        }
        if (boardRepository.findByWorkspaceKeyAndName(channel.getWorkspaceKey(), boardName).isPresent()) {
            boardName = "채널 " + channel.getId() + " 기본 보드";
        }
        KanbanBoard board = boardRepository.save(new KanbanBoard(
                channel.getWorkspaceKey(),
                boardName,
                "채널 업무 진행 보드",
                channel,
                creator
        ));
        columnRepository.save(new KanbanColumn(board, "할 일", 0));
        columnRepository.save(new KanbanColumn(board, "진행 중", 1));
        columnRepository.save(new KanbanColumn(board, "완료", 2));
        return board;
    }

    @Transactional
    public void deleteBoard(Long boardId) {
        KanbanBoard board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("보드를 찾을 수 없습니다."));
        boardRepository.delete(board);
    }

    @Transactional
    public KanbanColumnResponse addColumn(Long boardId, CreateKanbanColumnRequest request) {
        KanbanBoard board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("보드를 찾을 수 없습니다."));
        int sort = request.sortOrder() != null
                ? request.sortOrder()
                : columnRepository.findByBoard_IdOrderBySortOrderAsc(boardId).stream()
                        .mapToInt(KanbanColumn::getSortOrder)
                        .max()
                        .orElse(-1) + 1;
        KanbanColumn column = new KanbanColumn(board, request.name().trim(), sort);
        board.getColumns().add(column);
        board.touch();
        boardRepository.save(board);
        return new KanbanColumnResponse(column.getId(), column.getName(), column.getSortOrder(), List.of(), column.getCreatedAt());
    }

    @Transactional
    public KanbanColumnResponse updateColumn(Long boardId, Long columnId, UpdateKanbanColumnRequest request) {
        userRepository.findByEmployeeNo(request.actorEmployeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        KanbanColumn column = columnRepository.findByIdAndBoard_Id(columnId, boardId)
                .orElseThrow(() -> new IllegalArgumentException("컬럼을 찾을 수 없습니다."));
        column.setName(request.name().trim());
        column.setSortOrder(request.sortOrder());
        column.getBoard().touch();
        columnRepository.save(column);
        boardRepository.save(column.getBoard());
        List<KanbanCardResponse> cardResponses = column.getCards().stream().map(this::toCardResponse).toList();
        return new KanbanColumnResponse(
                column.getId(),
                column.getName(),
                column.getSortOrder(),
                cardResponses,
                column.getCreatedAt()
        );
    }

    @Transactional
    public void deleteColumn(Long boardId, Long columnId) {
        KanbanColumn column = columnRepository.findByIdAndBoard_Id(columnId, boardId)
                .orElseThrow(() -> new IllegalArgumentException("컬럼을 찾을 수 없습니다."));
        KanbanBoard board = column.getBoard();
        board.getColumns().remove(column);
        board.touch();
        columnRepository.delete(column);
        boardRepository.save(board);
    }

    /**
     * 채널 연동 보드: 해당 채널 멤버만 카드 변경 가능.
     * 워크스페이스 전용 보드: 앱 역할 MANAGER 이상만 카드 변경 가능.
     */
    private void assertCanMutateCard(KanbanBoard board, String actorEmployeeNo, AppRole callerRole) {
        String actor = actorEmployeeNo == null ? "" : actorEmployeeNo.trim();
        if (actor.isBlank()) {
            throw new IllegalArgumentException("actorEmployeeNo는 필수입니다.");
        }
        if (board.getSourceChannel() != null) {
            Long channelId = board.getSourceChannel().getId();
            if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, actor)) {
                throw new IllegalArgumentException("채널 멤버만 이 칸반 카드를 변경할 수 있습니다.");
            }
            return;
        }
        if (callerRole == null || !callerRole.atLeast(AppRole.MANAGER)) {
            throw new ForbiddenException("워크스페이스 칸반 카드는 매니저 이상만 변경할 수 있습니다.");
        }
    }

    /**
     * 채널 연동 보드에서는 담당자가 해당 채널 멤버여야 한다. 워크스페이스 전용 보드는 제한 없음.
     */
    private void assertAssigneeIsChannelMemberIfApplicable(KanbanBoard board, String assigneeEmployeeNo) {
        if (board.getSourceChannel() == null) {
            return;
        }
        String emp = assigneeEmployeeNo == null ? "" : assigneeEmployeeNo.trim();
        if (emp.isBlank()) {
            throw new IllegalArgumentException("담당자 사번이 비어 있습니다.");
        }
        Long channelId = board.getSourceChannel().getId();
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, emp)) {
            throw new IllegalArgumentException("해당 채널 멤버만 담당으로 지정할 수 있습니다.");
        }
    }

    @Transactional
    public KanbanCardResponse createCard(Long boardId, Long columnId, CreateKanbanCardRequest request, AppRole callerRole) {
        User actor = userRepository.findByEmployeeNo(request.actorEmployeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        KanbanColumn column = columnRepository.findByIdAndBoard_Id(columnId, boardId)
                .orElseThrow(() -> new IllegalArgumentException("컬럼을 찾을 수 없습니다."));
        assertCanMutateCard(column.getBoard(), request.actorEmployeeNo(), callerRole);
        int sort = request.sortOrder() != null
                ? request.sortOrder()
                : column.getCards().stream().mapToInt(KanbanCard::getSortOrder).max().orElse(-1) + 1;
        KanbanCard card = new KanbanCard(
                column,
                request.title().trim(),
                request.description() == null ? null : request.description(),
                sort,
                request.status()
        );
        column.getCards().add(card);
        column.getBoard().touch();
        card = cardRepository.save(card);
        boardRepository.save(column.getBoard());
        eventRepository.save(new KanbanCardEvent(
                card,
                actor,
                KanbanCardEventType.CARD_CREATED,
                null,
                card.getTitle()
        ));
        auditLogService.safeRecord(
                AuditEventType.KANBAN_CARD_CREATED,
                actor.getId(),
                "KANBAN_CARD",
                card.getId(),
                column.getBoard().getWorkspaceKey(),
                "boardId=" + column.getBoard().getId() + " columnId=" + columnId,
                null
        );
        applyInitialAssignees(card, actor, request.assigneeEmployeeNos());
        return toCardResponse(cardRepository.findById(card.getId()).orElseThrow());
    }

    private void applyInitialAssignees(KanbanCard card, User actor, List<String> assigneeEmployeeNos) {
        if (assigneeEmployeeNos == null || assigneeEmployeeNos.isEmpty()) {
            return;
        }
        if (assigneeEmployeeNos.size() > 50) {
            throw new IllegalArgumentException("담당자는 최대 50명까지 지정할 수 있습니다.");
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String raw : assigneeEmployeeNos) {
            if (raw == null) {
                continue;
            }
            String emp = raw.trim();
            if (emp.isBlank() || !seen.add(emp)) {
                continue;
            }
            assertAssigneeIsChannelMemberIfApplicable(card.getColumn().getBoard(), emp);
            User assignee = userRepository.findByEmployeeNo(emp)
                    .orElseThrow(() -> new IllegalArgumentException("담당자 사용자를 찾을 수 없습니다: " + emp));
            if (assigneeRepository.existsByCard_IdAndUser_EmployeeNo(card.getId(), emp)) {
                continue;
            }
            assigneeRepository.save(new KanbanCardAssignee(card, assignee));
            eventRepository.save(new KanbanCardEvent(
                    card,
                    actor,
                    KanbanCardEventType.ASSIGNEE_ADDED,
                    null,
                    assignee.getEmployeeNo()
            ));
        }
        card.touch();
        card.getColumn().getBoard().touch();
        cardRepository.save(card);
        boardRepository.save(card.getColumn().getBoard());
    }

    @Transactional
    public KanbanCardResponse updateCard(Long cardId, UpdateKanbanCardRequest request, AppRole callerRole) {
        User actor = userRepository.findByEmployeeNo(request.actorEmployeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        KanbanCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("카드를 찾을 수 없습니다."));
        KanbanBoard board = card.getColumn().getBoard();
        assertCanMutateCard(board, request.actorEmployeeNo(), callerRole);

        if (request.columnId() != null && !request.columnId().equals(card.getColumn().getId())) {
            KanbanColumn newColumn = columnRepository.findByIdAndBoard_Id(request.columnId(), board.getId())
                    .orElseThrow(() -> new IllegalArgumentException("이동 대상 컬럼을 찾을 수 없습니다."));
            String from = String.valueOf(card.getColumn().getId());
            String to = String.valueOf(newColumn.getId());
            eventRepository.save(new KanbanCardEvent(card, actor, KanbanCardEventType.COLUMN_MOVED, from, to));
            card.getColumn().getCards().remove(card);
            card.setColumn(newColumn);
            newColumn.getCards().add(card);
        }

        if (request.status() != null && !request.status().equals(card.getStatus())) {
            eventRepository.save(new KanbanCardEvent(
                    card,
                    actor,
                    KanbanCardEventType.STATUS_CHANGED,
                    card.getStatus(),
                    request.status().trim()
            ));
            card.setStatus(request.status().trim());
        }

        if (request.title() != null) {
            card.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            card.setDescription(request.description());
        }
        if (request.sortOrder() != null) {
            card.setSortOrder(request.sortOrder());
        }

        card.touch();
        board.touch();
        cardRepository.save(card);
        boardRepository.save(board);
        return toCardResponse(cardRepository.findById(cardId).orElseThrow());
    }

    @Transactional
    public void deleteCard(Long cardId, String actorEmployeeNo, AppRole callerRole) {
        KanbanCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("카드를 찾을 수 없습니다."));
        KanbanBoard board = card.getColumn().getBoard();
        assertCanMutateCard(board, actorEmployeeNo, callerRole);
        card.getColumn().getCards().remove(card);
        board.touch();
        cardRepository.delete(card);
        boardRepository.save(board);
    }

    @Transactional
    public KanbanCardResponse addAssignee(Long cardId, KanbanAssigneeMutationRequest request, AppRole callerRole) {
        KanbanCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("카드를 찾을 수 없습니다."));
        assertCanMutateCard(card.getColumn().getBoard(), request.actorEmployeeNo(), callerRole);
        assertAssigneeIsChannelMemberIfApplicable(card.getColumn().getBoard(), request.assigneeEmployeeNo());
        User actor = userRepository.findByEmployeeNo(request.actorEmployeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        User assignee = userRepository.findByEmployeeNo(request.assigneeEmployeeNo())
                .orElseThrow(() -> new IllegalArgumentException("담당자 사용자를 찾을 수 없습니다."));
        if (assigneeRepository.existsByCard_IdAndUser_EmployeeNo(cardId, request.assigneeEmployeeNo())) {
            throw new IllegalArgumentException("이미 담당으로 지정된 사용자입니다.");
        }
        assigneeRepository.save(new KanbanCardAssignee(card, assignee));
        eventRepository.save(new KanbanCardEvent(
                card,
                actor,
                KanbanCardEventType.ASSIGNEE_ADDED,
                null,
                assignee.getEmployeeNo()
        ));
        card.touch();
        card.getColumn().getBoard().touch();
        cardRepository.save(card);
        boardRepository.save(card.getColumn().getBoard());
        return toCardResponse(cardRepository.findById(cardId).orElseThrow());
    }

    @Transactional
    public KanbanCardResponse removeAssignee(Long cardId, String assigneeEmployeeNo, String actorEmployeeNo, AppRole callerRole) {
        KanbanCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("카드를 찾을 수 없습니다."));
        assertCanMutateCard(card.getColumn().getBoard(), actorEmployeeNo, callerRole);
        User actor = userRepository.findByEmployeeNo(actorEmployeeNo)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        KanbanCardAssignee link = assigneeRepository.findByCard_IdAndUser_EmployeeNo(cardId, assigneeEmployeeNo)
                .orElseThrow(() -> new IllegalArgumentException("해당 담당자가 없습니다."));
        assigneeRepository.delete(link);
        eventRepository.save(new KanbanCardEvent(
                card,
                actor,
                KanbanCardEventType.ASSIGNEE_REMOVED,
                assigneeEmployeeNo,
                null
        ));
        card.touch();
        card.getColumn().getBoard().touch();
        cardRepository.save(card);
        boardRepository.save(card.getColumn().getBoard());
        return toCardResponse(cardRepository.findById(cardId).orElseThrow());
    }

    public List<KanbanCardEventResponse> listCardHistory(Long cardId, Integer limit) {
        cardRepository.findById(cardId).orElseThrow(() -> new IllegalArgumentException("카드를 찾을 수 없습니다."));
        int size = limit == null ? 50 : Math.min(Math.max(limit, 1), MAX_HISTORY);
        return eventRepository
                .findByCard_IdOrderByCreatedAtDesc(cardId, PageRequest.of(0, size))
                .stream()
                .map(e -> new KanbanCardEventResponse(
                        e.getId(),
                        e.getEventType(),
                        e.getActor().getEmployeeNo(),
                        e.getFromRef(),
                        e.getToRef(),
                        e.getCreatedAt()
                ))
                .toList();
    }

    private KanbanBoardSummaryResponse toSummary(KanbanBoard board) {
        return new KanbanBoardSummaryResponse(
                board.getId(),
                board.getWorkspaceKey(),
                board.getName(),
                board.getDescription(),
                board.getCreatedBy().getEmployeeNo(),
                board.getCreatedAt(),
                board.getUpdatedAt()
        );
    }

    private KanbanCardResponse toCardResponse(KanbanCard card) {
        List<String> assigneeIds = card.getAssignees().stream()
                .map(a -> a.getUser().getEmployeeNo())
                .sorted()
                .toList();
        return new KanbanCardResponse(
                card.getId(),
                card.getColumn().getId(),
                card.getTitle(),
                card.getDescription(),
                card.getSortOrder(),
                card.getStatus(),
                assigneeIds,
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }
}
