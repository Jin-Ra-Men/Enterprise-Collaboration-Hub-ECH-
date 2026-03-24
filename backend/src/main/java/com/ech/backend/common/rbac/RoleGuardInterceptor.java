package com.ech.backend.common.rbac;

import com.ech.backend.common.exception.ForbiddenException;
import com.ech.backend.common.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RoleGuardInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        RequireRole requireRole = method.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = method.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null) {
            return true;
        }

        AppRole currentRole = resolveCurrentRole(request);
        if (currentRole == null) {
            throw new ForbiddenException("인증이 필요합니다. JWT 토큰을 Authorization 헤더에 포함해 주세요.");
        }
        if (!currentRole.atLeast(requireRole.value())) {
            throw new ForbiddenException("요청 권한이 부족합니다. 필요 권한: " + requireRole.value());
        }
        return true;
    }

    private AppRole resolveCurrentRole(HttpServletRequest request) {
        // 1순위: JWT 필터가 SecurityContext에 설정한 UserPrincipal
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.role();
        }
        // 2순위: 하위 호환 헤더 (개발/테스트 편의용, 운영에서는 JWT만 사용)
        String headerRole = request.getHeader("X-User-Role");
        return AppRole.parse(headerRole);
    }
}
