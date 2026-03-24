package com.ech.backend.api.auth;

import com.ech.backend.api.auth.dto.LoginRequest;
import com.ech.backend.api.auth.dto.LoginResponse;
import com.ech.backend.api.auth.dto.MeResponse;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 로그인. 사원번호 또는 이메일 + 비밀번호로 JWT를 발급한다.
     * 인증 없이 호출 가능 (SecurityConfig에서 permitAll 설정됨).
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 현재 로그인된 사용자 정보 조회.
     * JWT가 유효해야 호출 가능.
     */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(new MeResponse(
                principal.userId(),
                principal.employeeNo(),
                principal.email(),
                principal.name(),
                principal.department(),
                principal.role().name()
        ));
    }
}
