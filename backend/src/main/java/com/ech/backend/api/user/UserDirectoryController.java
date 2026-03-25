package com.ech.backend.api.user;

import com.ech.backend.api.user.dto.OrganizationTreeResponse;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 목록·조직도 등 UI용 조회 API.
 * {@code /api/users/...} 는 정적 리소스(프론트 폴더)와 경로가 겹쳐 404가 나는 환경이 있어, 전용 prefix 로 분리한다.
 */
@RestController
@RequestMapping("/api/user-directory")
@RequireRole(AppRole.MEMBER)
public class UserDirectoryController {

    private final UserSearchService userSearchService;

    public UserDirectoryController(UserSearchService userSearchService) {
        this.userSearchService = userSearchService;
    }

    @GetMapping("/organization")
    public ApiResponse<OrganizationTreeResponse> organization() {
        return ApiResponse.success(userSearchService.getOrganizationTree());
    }
}
