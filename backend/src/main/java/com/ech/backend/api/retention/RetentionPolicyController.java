package com.ech.backend.api.retention;

import com.ech.backend.api.retention.dto.ArchiveRunResultResponse;
import com.ech.backend.api.retention.dto.RetentionPolicyResponse;
import com.ech.backend.api.retention.dto.UpdateRetentionPolicyRequest;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/retention-policies")
@RequireRole(AppRole.ADMIN)
public class RetentionPolicyController {

    private final RetentionPolicyService retentionPolicyService;

    public RetentionPolicyController(RetentionPolicyService retentionPolicyService) {
        this.retentionPolicyService = retentionPolicyService;
    }

    /**
     * 전체 보존 정책 목록 조회.
     */
    @GetMapping
    public ApiResponse<List<RetentionPolicyResponse>> listAll() {
        return ApiResponse.success(retentionPolicyService.listAll());
    }

    /**
     * 특정 자원 유형의 보존 정책 수정.
     *
     * @param resourceType MESSAGES | AUDIT_LOGS | ERROR_LOGS
     */
    @PutMapping("/{resourceType}")
    public ApiResponse<RetentionPolicyResponse> update(
            @PathVariable String resourceType,
            @Valid @RequestBody UpdateRetentionPolicyRequest request
    ) {
        return ApiResponse.success(retentionPolicyService.updatePolicy(resourceType, request));
    }

    /**
     * 활성화된 모든 보존 정책 수동 실행.
     * 스케줄러를 기다리지 않고 즉시 아카이빙을 실행할 때 사용.
     */
    @PostMapping("/trigger")
    public ApiResponse<List<ArchiveRunResultResponse>> triggerAll() {
        return ApiResponse.success(retentionPolicyService.runArchiving());
    }

    /**
     * 특정 자원 유형 보존 정책 수동 실행 (활성화 여부 무관).
     *
     * @param resourceType MESSAGES | AUDIT_LOGS | ERROR_LOGS
     */
    @PostMapping("/trigger/{resourceType}")
    public ApiResponse<ArchiveRunResultResponse> triggerOne(
            @PathVariable String resourceType
    ) {
        return ApiResponse.success(retentionPolicyService.runArchivingForType(resourceType));
    }
}
