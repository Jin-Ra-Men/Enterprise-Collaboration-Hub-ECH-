package com.ech.backend.api.work;

import com.ech.backend.api.work.dto.CreateWorkItemFromMessageRequest;
import com.ech.backend.api.work.dto.WorkItemResponse;
import com.ech.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
}
