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
        long profileImageVersion,
        /** Master toggle: false면 게이트웨이·프로액티브 제안함 등 AI 기능 미사용. */
        boolean aiAssistantEnabled
) {
}
