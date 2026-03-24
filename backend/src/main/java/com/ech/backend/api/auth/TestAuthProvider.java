package com.ech.backend.api.auth;

import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 로컬 DB 기반 테스트 인증 제공자.
 * 사원번호 또는 이메일로 사용자를 조회하고, BCrypt 비밀번호를 검증한다.
 *
 * 그룹웨어 연동 시: 새 {@code GroupwareAuthProvider}를 {@link AuthProvider}를 구현해 추가하고,
 * {@link AuthService}에서 원하는 제공자를 선택하면 기존 코드를 변경하지 않아도 된다.
 */
@Component
public class TestAuthProvider implements AuthProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestAuthProvider(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String getSourceName() {
        return "TEST";
    }

    @Override
    public Optional<User> authenticate(String loginId, String rawPassword) {
        if (loginId == null || rawPassword == null) {
            return Optional.empty();
        }
        // 사원번호 또는 이메일로 조회
        Optional<User> userOpt = userRepository.findByEmail(loginId.trim());
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmployeeNo(loginId.trim());
        }
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        User user = userOpt.get();

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return Optional.empty();
        }
        if (user.getPasswordHash() == null) {
            return Optional.empty();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            return Optional.empty();
        }
        return Optional.of(user);
    }
}
