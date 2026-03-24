package com.ech.backend.api.auth;

import com.ech.backend.domain.user.User;
import java.util.Optional;

/**
 * 인증 제공자 인터페이스.
 * 현재는 {@link TestAuthProvider}(로컬 DB 검증)만 구현되어 있으며,
 * 추후 그룹웨어 SSO 연동 시 {@code GroupwareAuthProvider}를 추가하면 된다.
 */
public interface AuthProvider {

    /** 이 제공자의 이름 (예: "TEST", "GROUPWARE") */
    String getSourceName();

    /**
     * 자격 증명을 검증하고 인증된 사용자를 반환한다.
     *
     * @param loginId   사원번호 또는 이메일
     * @param rawPassword 평문 비밀번호
     * @return 인증 성공 시 User, 실패 시 empty
     */
    Optional<User> authenticate(String loginId, String rawPassword);
}
