package com.ech.backend.api.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateKanbanCardRequest(
        @NotBlank @Size(max = 50) String actorEmployeeNo,
        /** Parent work item (sub-task). Must belong to the same channel as the board. */
        @NotNull Long workItemId,
        @NotBlank @Size(max = 500) String title,
        @Size(max = 8000) String description,
        Integer sortOrder,
        @Size(max = 50) String status,
        /** 선택. 카드 생성 직후 지정할 담당자 사번 목록(중복·빈 값은 서비스에서 무시, 최대 50명). */
        List<String> assigneeEmployeeNos
) {
}
