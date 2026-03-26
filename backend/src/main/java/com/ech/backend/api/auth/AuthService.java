package com.ech.backend.api.auth;

import com.ech.backend.api.auth.dto.LoginRequest;
import com.ech.backend.api.auth.dto.LoginResponse;
import com.ech.backend.common.exception.UnauthorizedException;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.security.JwtUtil;
import com.ech.backend.common.security.UserPrincipal;
import com.ech.backend.domain.org.OrgGroupMember;
import com.ech.backend.domain.org.OrgGroupMemberRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Set<String> ALLOWED_THEMES = Set.of("dark", "light", "blue");

    private final List<AuthProvider> authProviders;
    private final JwtUtil jwtUtil;
    private final OrgGroupMemberRepository orgGroupMemberRepository;
    private final UserRepository userRepository;

    public AuthService(
            List<AuthProvider> authProviders,
            JwtUtil jwtUtil,
            OrgGroupMemberRepository orgGroupMemberRepository,
            UserRepository userRepository
    ) {
        this.authProviders = authProviders;
        this.jwtUtil = jwtUtil;
        this.orgGroupMemberRepository = orgGroupMemberRepository;
        this.userRepository = userRepository;
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

        String resolvedDepartment = resolveDepartmentFromTeam(user);

        AppRole role = AppRole.parse(user.getRole());
        if (role == null) {
            role = AppRole.MEMBER;
        }
        UserPrincipal principal = new UserPrincipal(
                user.getId(),
                user.getEmployeeNo(),
                user.getEmail(),
                user.getName(),
                resolvedDepartment,
                role
        );
        String token = jwtUtil.generateToken(principal);

        return new LoginResponse(
                token,
                user.getId(),
                user.getEmployeeNo(),
                user.getEmail(),
                user.getName(),
                resolvedDepartment,
                role.name(),
                normalizeThemeOrDefault(user.getThemePreference())
        );
    }

    @Transactional
    public String updateThemePreference(Long userId, String rawTheme) {
        String normalized = normalizeThemeOrDefault(rawTheme);
        if (userRepository.updateThemePreferenceById(userId, normalized) <= 0) {
            throw new UnauthorizedException("사용자 정보를 찾을 수 없습니다.");
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    public String getThemePreference(Long userId) {
        return userRepository.findById(userId)
                .map(User::getThemePreference)
                .map(this::normalizeThemeOrDefault)
                .orElse("dark");
    }

    private String resolveDepartmentFromTeam(User user) {
        List<OrgGroupMember> members = orgGroupMemberRepository.findMembersByMemberGroupTypeAndEmployeeNos(
                "TEAM",
                List.of(user.getEmployeeNo())
        );
        if (members == null || members.isEmpty()) {
            return "";
        }
        String displayName = members.get(0).getGroup().getDisplayName();
        return (displayName != null && !displayName.isBlank()) ? displayName : "";
    }

    private String normalizeThemeOrDefault(String rawTheme) {
        if (rawTheme == null || rawTheme.isBlank()) {
            return "dark";
        }
        String normalized = rawTheme.trim().toLowerCase();
        return ALLOWED_THEMES.contains(normalized) ? normalized : "dark";
    }
}
