package com.ech.backend.api.work;

import com.ech.backend.api.work.dto.CreateWorkItemFromMessageRequest;
import com.ech.backend.api.work.dto.CreateWorkItemRequest;
import com.ech.backend.api.work.dto.UpdateWorkItemRequest;
import com.ech.backend.api.work.dto.WorkItemResponse;
import com.ech.backend.api.work.dto.WorkItemSidebarResponse;
import com.ech.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkItemController {

    private final WorkItemService workItemService;

    public WorkItemController(WorkItemService workItemService) {
        this.workItemService = workItemService;
    }

    @PostMapping("/api/messages/{messageId}/work-items")
    public ApiResponse<WorkItemResponse> createFromMessage(
            @PathVariable Long messageId,
            @Valid @RequestBody CreateWorkItemFromMessageRequest request
    ) {
        return ApiResponse.success(workItemService.createFromMessage(messageId, request));
    }

    @GetMapping("/api/messages/{messageId}/work-items")
    public ApiResponse<List<WorkItemResponse>> listByMessage(@PathVariable Long messageId) {
        return ApiResponse.success(workItemService.listByMessageId(messageId));
    }

    @GetMapping("/api/work-items/{workItemId}")
    public ApiResponse<WorkItemResponse> getById(@PathVariable Long workItemId) {
        return ApiResponse.success(workItemService.getById(workItemId));
    }

    /**
     * Sidebar: work items where the user is assignee on at least one kanban card (sub-task).
     * Path avoids collision with {@code GET /api/work-items/{workItemId}}.
     */
    @GetMapping("/api/work-items/sidebar/by-assigned-cards")
    public ApiResponse<List<WorkItemSidebarResponse>> listWorkItemsWithMyCardAssignment(
            @RequestParam String employeeNo,
            @RequestParam(required = false, defaultValue = "30") Integer limit
    ) {
        return ApiResponse.success(
                workItemService.listWorkItemsWithMyCardAssignment(employeeNo, limit == null ? 30 : limit));
    }

    @GetMapping("/api/channels/{channelId}/work-items")
    public ApiResponse<List<WorkItemResponse>> listByChannel(
            @PathVariable Long channelId,
            @RequestParam String employeeNo,
            @RequestParam(required = false, defaultValue = "50") Integer limit
    ) {
        return ApiResponse.success(workItemService.listByChannelId(channelId, employeeNo, limit == null ? 50 : limit));
    }

    @PostMapping("/api/channels/{channelId}/work-items")
    public ApiResponse<WorkItemResponse> createInChannel(
            @PathVariable Long channelId,
            @Valid @RequestBody CreateWorkItemRequest request
    ) {
        return ApiResponse.success(workItemService.createInChannel(channelId, request));
    }

    @PutMapping("/api/work-items/{workItemId}")
    public ApiResponse<WorkItemResponse> updateWorkItem(
            @PathVariable Long workItemId,
            @Valid @RequestBody UpdateWorkItemRequest request
    ) {
        return ApiResponse.success(workItemService.updateWorkItem(workItemId, request));
    }

    @PostMapping("/api/work-items/{workItemId}/restore")
    public ApiResponse<WorkItemResponse> restoreWorkItem(
            @PathVariable Long workItemId,
            @RequestParam String actorEmployeeNo
    ) {
        return ApiResponse.success(workItemService.restoreWorkItem(workItemId, actorEmployeeNo));
    }

    /**
     * Default: soft-delete ({@code in_use = false}). {@code hard=true}: delete all linked cards and the work item row.
     */
    @DeleteMapping("/api/work-items/{workItemId}")
    public ApiResponse<Void> deleteWorkItem(
            @PathVariable Long workItemId,
            @RequestParam String actorEmployeeNo,
            @RequestParam(required = false, defaultValue = "false") boolean hard
    ) {
        if (hard) {
            workItemService.purgeWorkItem(workItemId, actorEmployeeNo);
        } else {
            workItemService.deleteWorkItem(workItemId, actorEmployeeNo);
        }
        return ApiResponse.success(null);
    }
}
