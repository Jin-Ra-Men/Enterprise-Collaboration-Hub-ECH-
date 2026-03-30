package com.ech.backend.util;

import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.security.JwtUtil;
import com.ech.backend.common.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtil 단위 테스트")
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String SECRET = "ech-test-jwt-secret-key-minimum-32-chars!!";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3_600_000L);
    }

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(101L, "EMP001", "admin@test.com", "관리자", "IT", AppRole.ADMIN);
    }

    @Test
    @DisplayName("JWT 생성 후 parseToken으로 employeeNo 복원")
    void generate_and_parse_employeeNo() {
        String token = jwtUtil.generateToken(adminPrincipal());
        Optional<UserPrincipal> parsed = jwtUtil.parseToken(token);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().employeeNo()).isEqualTo("EMP001");
        assertThat(parsed.get().userId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("JWT 생성 후 이메일 복원 일치")
    void generate_and_parse_email() {
        String token = jwtUtil.generateToken(adminPrincipal());
        Optional<UserPrincipal> parsed = jwtUtil.parseToken(token);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().email()).isEqualTo("admin@test.com");
    }

    @Test
    @DisplayName("JWT 생성 후 역할 복원 일치")
    void generate_and_parse_role() {
        String token = jwtUtil.generateToken(adminPrincipal());
        Optional<UserPrincipal> parsed = jwtUtil.parseToken(token);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().role()).isEqualTo(AppRole.ADMIN);
    }

    @Test
    @DisplayName("변조된 토큰은 parseToken이 빈 값을 반환")
    void tampered_token_returns_empty() {
        String token = jwtUtil.generateToken(adminPrincipal()) + "tampered";
        assertThat(jwtUtil.parseToken(token)).isEmpty();
    }

    @Test
    @DisplayName("빈 문자열 토큰은 parseToken이 빈 값을 반환")
    void empty_token_returns_empty() {
        assertThat(jwtUtil.parseToken("")).isEmpty();
    }

    @Test
    @DisplayName("MEMBER 역할로 토큰 생성/복원")
    void member_role_token() {
        UserPrincipal member = new UserPrincipal(202L, "EMP002", "member@test.com",
                "일반사용자", "개발팀", AppRole.MEMBER);
        String token = jwtUtil.generateToken(member);
        Optional<UserPrincipal> parsed = jwtUtil.parseToken(token);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().role()).isEqualTo(AppRole.MEMBER);
    }
}
