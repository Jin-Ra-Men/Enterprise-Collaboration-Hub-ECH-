package com.ech.backend.api.user;

import com.ech.backend.api.user.dto.DepartmentGroupResponse;
import com.ech.backend.api.user.dto.UserProfileResponse;
import com.ech.backend.api.user.dto.UserSearchResponse;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequireRole(AppRole.MEMBER)
public class UserSearchController {

    private final UserSearchService userSearchService;

    public UserSearchController(UserSearchService userSearchService) {
        this.userSearchService = userSearchService;
    }

    @GetMapping("/search")
    public ApiResponse<List<UserSearchResponse>> searchUsers(
            @RequestParam(required = false, name = "q") String keyword,
            @RequestParam(required = false) String department
    ) {
        return ApiResponse.success(userSearchService.searchUsers(keyword, department));
    }

    /** ACTIVE 사용자를 부서 단위로 묶어 조직도 선택 UI에 사용한다. */
    @GetMapping("/organization")
    public ApiResponse<List<DepartmentGroupResponse>> listOrganization() {
        return ApiResponse.success(userSearchService.listUsersGroupedByDepartment());
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<UserProfileResponse> getProfile(@PathVariable Long userId) {
        return ApiResponse.success(userSearchService.getProfile(userId));
    }
}
