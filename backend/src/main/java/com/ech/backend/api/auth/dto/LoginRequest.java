package com.ech.backend.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        /** 사원번호 또는 이메일 */
        @NotBlank String loginId,
        @NotBlank String password
) {
}
