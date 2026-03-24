package com.ech.backend.common.security;

import com.ech.backend.common.rbac.AppRole;

/**
 * JWT에서 추출한 인증된 사용자 정보. SecurityContext에 저장된다.
 */
public record UserPrincipal(
        Long userId,
        String employeeNo,
        String email,
        String name,
        String department,
        AppRole role
) {
}
