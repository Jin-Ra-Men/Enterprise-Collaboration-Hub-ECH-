package com.ech.backend.api.auth.dto;

public record MeResponse(
        Long userId,
        String employeeNo,
        String email,
        String name,
        String department,
        String role,
        String themePreference,
        /** {@code app.allow-user-profile-self-upload} — 추후 false 로 본인 변경 UI 비활성화 가능. */
        boolean profileSelfUploadAllowed,
        boolean profileImagePresent,
        long profileImageVersion
) {
}
