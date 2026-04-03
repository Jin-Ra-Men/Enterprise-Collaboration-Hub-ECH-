package com.ech.backend.api.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 관리자가 app_settings 행을 새로 추가할 때 사용.
 */
public record CreateSettingRequest(
        @NotBlank
        @Size(min = 1, max = 100)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "키는 영문·숫자·점·하이픈·밑줄만 사용할 수 있습니다")
        String key,
        String value,
        @Size(max = 2000) String description,
        @Size(max = 50) String updatedBy
) {
}
