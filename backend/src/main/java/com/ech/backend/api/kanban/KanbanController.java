package com.ech.backend.api.kanban;

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
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.exception.UnauthorizedException;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import com.ech.backend.common.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kanban")
public class KanbanController {

    private final KanbanService kanbanService;

    public KanbanController(KanbanService kanbanService) {
        this.kanbanService = kanbanService;
    }

    @PostMapping("/boards")
    @RequireRole(AppRole.MANAGER)
    public ApiResponse<KanbanBoardSummaryResponse> createBoard(@Valid @RequestBody CreateKanbanBoardRequest request) {
        return ApiResponse.success(kanbanService.createBoard(request));
    }

    @GetMapping("/boards")
    public ApiResponse<List<KanbanBoardSummaryResponse>> listBoards(
            @RequestParam(required = false, defaultValue = "default") String workspaceKey
    ) {
        return ApiResponse.success(kanbanService.listBoards(workspaceKey));
    }

    @GetMapping("/boards/{boardId}")
    public ApiResponse<KanbanBoardDetailResponse> getBoard(@PathVariable Long boardId) {
        return ApiResponse.success(kanbanService.getBoard(boardId));
    }

    @GetMapping("/channels/{channelId}/board")
    public ApiResponse<KanbanBoardDetailResponse> getOrCreateChannelBoard(
            @PathVariable Long channelId,
            @RequestParam String employeeNo
    ) {
        return ApiResponse.success(kanbanService.getOrCreateChannelBoard(channelId, employeeNo));
    }

    @DeleteMapping("/boards/{boardId}")
    @RequireRole(AppRole.MANAGER)
    public ApiResponse<Void> deleteBoard(@PathVariable Long boardId) {
        kanbanService.deleteBoard(boardId);
        return ApiResponse.success(null);
    }

    @PostMapping("/boards/{boardId}/columns")
    @RequireRole(AppRole.MANAGER)
    public ApiResponse<KanbanColumnResponse> addColumn(
            @PathVariable Long boardId,
            @Valid @RequestBody CreateKanbanColumnRequest request
    ) {
        return ApiResponse.success(kanbanService.addColumn(boardId, request));
    }

    @PutMapping("/boards/{boardId}/columns/{columnId}")
    @RequireRole(AppRole.MANAGER)
    public ApiResponse<KanbanColumnResponse> updateColumn(
            @PathVariable Long boardId,
            @PathVariable Long columnId,
            @Valid @RequestBody UpdateKanbanColumnRequest request
    ) {
        return ApiResponse.success(kanbanService.updateColumn(boardId, columnId, request));
    }

    @DeleteMapping("/boards/{boardId}/columns/{columnId}")
    @RequireRole(AppRole.MANAGER)
    public ApiResponse<Void> deleteColumn(@PathVariable Long boardId, @PathVariable Long columnId) {
        kanbanService.deleteColumn(boardId, columnId);
        return ApiResponse.success(null);
    }

    @PostMapping("/boards/{boardId}/columns/{columnId}/cards")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<KanbanCardResponse> createCard(
            @PathVariable Long boardId,
            @PathVariable Long columnId,
            @Valid @RequestBody CreateKanbanCardRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return ApiResponse.success(kanbanService.createCard(boardId, columnId, request, principal.role()));
    }

    @PutMapping("/cards/{cardId}")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<KanbanCardResponse> updateCard(
            @PathVariable Long cardId,
            @Valid @RequestBody UpdateKanbanCardRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return ApiResponse.success(kanbanService.updateCard(cardId, request, principal.role()));
    }

    @DeleteMapping("/cards/{cardId}")
    @RequireRole(AppRole.MANAGER)
    public ApiResponse<Void> deleteCard(@PathVariable Long cardId) {
        kanbanService.deleteCard(cardId);
        return ApiResponse.success(null);
    }

    @PostMapping("/cards/{cardId}/assignees")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<KanbanCardResponse> addAssignee(
            @PathVariable Long cardId,
            @Valid @RequestBody KanbanAssigneeMutationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return ApiResponse.success(kanbanService.addAssignee(cardId, request, principal.role()));
    }

    @DeleteMapping("/cards/{cardId}/assignees/{assigneeEmployeeNo}")
    @RequireRole(AppRole.MEMBER)
    public ApiResponse<KanbanCardResponse> removeAssignee(
            @PathVariable Long cardId,
            @PathVariable String assigneeEmployeeNo,
            @RequestParam String actorEmployeeNo,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return ApiResponse.success(
                kanbanService.removeAssignee(cardId, assigneeEmployeeNo, actorEmployeeNo, principal.role()));
    }

    @GetMapping("/cards/{cardId}/history")
    public ApiResponse<List<KanbanCardEventResponse>> cardHistory(
            @PathVariable Long cardId,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(kanbanService.listCardHistory(cardId, limit));
    }
}
