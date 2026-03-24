package com.ech.backend.common.rbac;

import com.ech.backend.common.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

        String headerRole = request.getHeader("X-User-Role");
        AppRole currentRole = AppRole.parse(headerRole);
        if (currentRole == null) {
            throw new ForbiddenException("권한 헤더가 없거나 올바르지 않습니다. X-User-Role(MEMBER|MANAGER|ADMIN)을 설정해주세요.");
        }
        if (!currentRole.atLeast(requireRole.value())) {
            throw new ForbiddenException("요청 권한이 부족합니다. 필요 권한: " + requireRole.value());
        }
        return true;
    }
}
