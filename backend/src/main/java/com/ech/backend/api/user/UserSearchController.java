package com.ech.backend.api.user;

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

    /**
     * 프로필 조회(쿼리 파라미터).
     * 프론트엔드 기본 연동 경로. 일부 환경에서 경로형 {@code /{userId}/profile}이 404가 될 때 대비.
     */
    @GetMapping(value = "/profile", params = "userId")
    public ApiResponse<UserProfileResponse> getProfileByQuery(@RequestParam Long userId) {
        return ApiResponse.success(userSearchService.getProfile(userId));
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<UserProfileResponse> getProfile(@PathVariable Long userId) {
        return ApiResponse.success(userSearchService.getProfile(userId));
    }
}
