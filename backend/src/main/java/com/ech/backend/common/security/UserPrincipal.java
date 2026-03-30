package com.ech.backend.common.security;

import com.ech.backend.common.rbac.AppRole;

/**
 * JWT에서 추출한 인증된 사용자 정보. SecurityContext에 저장된다.
 *
 * @param userId DB {@code users.id}. JWT {@code uid} 클레임과 동기화되며, 레거시 토큰에서는 null일 수 있다.
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
