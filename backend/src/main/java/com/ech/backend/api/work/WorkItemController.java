package com.ech.backend.api.work;

import com.ech.backend.api.work.dto.CreateWorkItemFromMessageRequest;
import com.ech.backend.api.work.dto.CreateWorkItemRequest;
import com.ech.backend.api.work.dto.UpdateWorkItemRequest;
import com.ech.backend.api.work.dto.WorkItemResponse;
import com.ech.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
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
}
