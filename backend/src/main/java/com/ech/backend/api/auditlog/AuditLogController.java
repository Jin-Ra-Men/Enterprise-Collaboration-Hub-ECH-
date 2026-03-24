package com.ech.backend.api.auditlog;

import com.ech.backend.api.auditlog.dto.AuditLogResponse;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequireRole(AppRole.ADMIN)
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 감사 로그 목록 조회 (관리자 전용)
     *
     * <p>쿼리 파라미터:
     * <ul>
     *   <li>from / to: ISO-8601 일시 범위 필터</li>
     *   <li>actorUserId: 행위자 사용자 ID</li>
     *   <li>eventType: AuditEventType enum 이름 (예: CHANNEL_CREATED)</li>
     *   <li>resourceType: 리소스 유형 (CHANNEL, MESSAGE, FILE, WORK_ITEM, KANBAN_BOARD, ...)</li>
     *   <li>workspaceKey: 워크스페이스 키</li>
     *   <li>limit: 최대 조회 건수 (기본 100, 최대 500)</li>
     * </ul>
     */
    @GetMapping
    public ApiResponse<List<AuditLogResponse>> search(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String workspaceKey,
            @RequestParam(required = false) Integer limit
    ) {
        List<AuditLogResponse> result = auditLogService.search(
                from, to, actorUserId, eventType, resourceType, workspaceKey, limit);
        return ApiResponse.success(result);
    }
}
