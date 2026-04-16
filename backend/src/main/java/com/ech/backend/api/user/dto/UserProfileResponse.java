package com.ech.backend.api.user.dto;

public record UserProfileResponse(
        Long userId,
        String employeeNo,
        String name,
        String email,
        String department,
        String jobLevel,
        String jobPosition,
        String jobTitle,
        String role,
        String status,
        /** 프로필 사진 존재 여부(캐시·표시용). */
        boolean profileImagePresent,
        /** 프로필 사진 캐시 무효화용(보통 사용자 {@code updated_at} 기반 epoch ms). */
        long profileImageVersion
) {
}
