package com.ech.backend.api.retention.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRetentionPolicyRequest(
        /** 보존 기간(일). 0 이하이면 영구 보관. */
        @NotNull @Min(0) Integer retentionDays,
        /** 자동 아카이빙 활성 여부 */
        @NotNull Boolean isEnabled,
        /** 정책 설명 (선택) */
        String description,
        /** 변경자 사용자 사번 */
        @Size(max = 50) String updatedBy
) {
}
