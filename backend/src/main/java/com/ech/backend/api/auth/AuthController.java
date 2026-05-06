package com.ech.backend.api.auth;

import com.ech.backend.api.auth.dto.AdLoginRequest;
import com.ech.backend.api.auth.dto.LoginRequest;
import com.ech.backend.api.auth.dto.LoginResponse;
import com.ech.backend.api.auth.dto.MeResponse;
import com.ech.backend.api.auth.dto.UpdateThemePreferenceRequest;
import com.ech.backend.api.aiassistant.AiAssistantService;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.exception.UnauthorizedException;
import com.ech.backend.common.security.UserPrincipal;
import com.ech.backend.domain.user.User;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AiAssistantService aiAssistantService;

    @Value("${app.allow-user-profile-self-upload:true}")
    private boolean allowUserProfileSelfUpload;

    public AuthController(AuthService authService, AiAssistantService aiAssistantService) {
        this.authService = authService;
        this.aiAssistantService = aiAssistantService;
    }

    /**
     * 일반 로그인. 사원번호 또는 이메일 + 비밀번호로 JWT를 발급한다.
     * 인증 없이 호출 가능 (SecurityConfig에서 permitAll 설정됨).
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * AD 자동 로그인 (Electron 전용).
     * AD 도메인 가입 PC에서 Electron이 Windows 계정명(sAMAccountName)을 전송하면 JWT를 발급한다.
     * 인증 없이 호출 가능 (SecurityConfig에서 permitAll 설정됨).
     */
    @PostMapping("/ad-login")
    public ApiResponse<LoginResponse> adLogin(@Valid @RequestBody AdLoginRequest request) {
        return ApiResponse.success(authService.adLogin(request));
    }

    /**
     * 현재 로그인된 사용자 정보 조회.
     * JWT가 유효해야 호출 가능.
     */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        User user = authService.findUserForPrincipal(principal)
                .orElseThrow(() -> new UnauthorizedException("사용자 정보를 찾을 수 없습니다."));
        if (user.getEmployeeNo() == null || user.getEmployeeNo().isBlank()) {
            throw new UnauthorizedException("계정에 사원번호가 없습니다. 관리자에게 문의하세요.");
        }
        String themePreference = authService.getThemePreference(user.getEmployeeNo());
        boolean present = user.getProfileImageRelPath() != null && !user.getProfileImageRelPath().isBlank();
        long ver = user.getUpdatedAt() != null ? user.getUpdatedAt().toInstant().toEpochMilli() : 0L;
        boolean aiOn = aiAssistantService.isAiAssistantEnabled(user.getEmployeeNo());
        return ApiResponse.success(new MeResponse(
                user.getId(),
                user.getEmployeeNo(),
                user.getEmail(),
                user.getName(),
                principal.department(),
                principal.role().name(),
                themePreference,
                allowUserProfileSelfUpload,
                present,
                ver,
                aiOn
        ));
    }

    @PutMapping("/me/theme")
    public ApiResponse<String> updateTheme(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateThemePreferenceRequest request
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        User user = authService.findUserForPrincipal(principal)
                .orElseThrow(() -> new UnauthorizedException("사용자 정보를 찾을 수 없습니다."));
        return ApiResponse.success(authService.updateThemePreference(user.getEmployeeNo(), request.theme()));
    }
}
