package com.ech.backend.api.auth;

import com.ech.backend.api.auth.dto.LoginRequest;
import com.ech.backend.api.auth.dto.LoginResponse;
import com.ech.backend.common.exception.UnauthorizedException;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.security.JwtUtil;
import com.ech.backend.common.security.UserPrincipal;
import com.ech.backend.domain.user.User;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final List<AuthProvider> authProviders;
    private final JwtUtil jwtUtil;

    public AuthService(List<AuthProvider> authProviders, JwtUtil jwtUtil) {
        this.authProviders = authProviders;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 로그인 처리. 등록된 AuthProvider를 순서대로 시도하며 최초 성공 시 JWT를 발급한다.
     * 현재는 TestAuthProvider만 존재하며, 그룹웨어 연동 시 GroupwareAuthProvider가 추가된다.
     *
     * @throws IllegalArgumentException 자격 증명 불일치 또는 비활성 계정
     */
    public LoginResponse login(LoginRequest request) {
        User user = null;
        for (AuthProvider provider : authProviders) {
            user = provider.authenticate(request.loginId(), request.password()).orElse(null);
            if (user != null) {
                break;
            }
        }
        if (user == null) {
            throw new UnauthorizedException("사원번호/이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        AppRole role = AppRole.parse(user.getRole());
        if (role == null) {
            role = AppRole.MEMBER;
        }
        UserPrincipal principal = new UserPrincipal(
                user.getId(),
                user.getEmployeeNo(),
                user.getEmail(),
                user.getName(),
                user.getDepartment(),
                role
        );
        String token = jwtUtil.generateToken(principal);

        return new LoginResponse(
                token,
                user.getId(),
                user.getEmployeeNo(),
                user.getEmail(),
                user.getName(),
                user.getDepartment(),
                role.name()
        );
    }
}
