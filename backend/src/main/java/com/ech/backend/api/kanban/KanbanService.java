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
import com.ech.backend.domain.kanban.KanbanColumn;
import com.ech.backend.domain.kanban.KanbanColumnRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    public KanbanService(
            KanbanBoardRepository boardRepository,
            KanbanColumnRepository columnRepository,
            KanbanCardRepository cardRepository,
            KanbanCardAssigneeRepository assigneeRepository,
            KanbanCardEventRepository eventRepository,
            UserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.cardRepository = cardRepository;
        this.assigneeRepository = assigneeRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
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

    @Transactional
    public KanbanCardResponse createCard(Long boardId, Long columnId, CreateKanbanCardRequest request) {
        User actor = userRepository.findByEmployeeNo(request.actorEmployeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        KanbanColumn column = columnRepository.findByIdAndBoard_Id(columnId, boardId)
                .orElseThrow(() -> new IllegalArgumentException("컬럼을 찾을 수 없습니다."));
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
        return toCardResponse(cardRepository.findById(card.getId()).orElseThrow());
    }

    @Transactional
    public KanbanCardResponse updateCard(Long cardId, UpdateKanbanCardRequest request) {
        User actor = userRepository.findByEmployeeNo(request.actorEmployeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        KanbanCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("카드를 찾을 수 없습니다."));
        KanbanBoard board = card.getColumn().getBoard();

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
    public void deleteCard(Long cardId) {
        KanbanCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("카드를 찾을 수 없습니다."));
        KanbanBoard board = card.getColumn().getBoard();
        card.getColumn().getCards().remove(card);
        board.touch();
        cardRepository.delete(card);
        boardRepository.save(board);
    }

    @Transactional
    public KanbanCardResponse addAssignee(Long cardId, KanbanAssigneeMutationRequest request) {
        User actor = userRepository.findByEmployeeNo(request.actorEmployeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        User assignee = userRepository.findByEmployeeNo(request.assigneeEmployeeNo())
                .orElseThrow(() -> new IllegalArgumentException("담당자 사용자를 찾을 수 없습니다."));
        KanbanCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("카드를 찾을 수 없습니다."));
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
    public KanbanCardResponse removeAssignee(Long cardId, String assigneeEmployeeNo, String actorEmployeeNo) {
        User actor = userRepository.findByEmployeeNo(actorEmployeeNo)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        KanbanCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("카드를 찾을 수 없습니다."));
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
