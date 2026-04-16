package com.ech.backend.api.admin.user;

import com.ech.backend.api.admin.user.dto.AdminUserListItemResponse;
import com.ech.backend.api.admin.user.dto.AdminUserSaveRequest;
import com.ech.backend.api.admin.user.dto.OrgGroupOptionResponse;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/users")
@RequireRole(AppRole.ADMIN)
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** 전체 사용자 목록 (조직 정보 포함) */
    @GetMapping
    public ApiResponse<List<AdminUserListItemResponse>> listUsers() {
        return ApiResponse.success(adminUserService.getAllUsers());
    }

    /** 조직 그룹 드롭다운 옵션 (부서/직급/직위/직책) */
    @GetMapping("/org-options")
    public ApiResponse<OrgGroupOptionResponse> orgOptions() {
        return ApiResponse.success(adminUserService.getOrgGroupOptions());
    }

    /** 사용자 등록 */
    @PostMapping
    public ApiResponse<AdminUserListItemResponse> createUser(@Valid @RequestBody AdminUserSaveRequest req) {
        return ApiResponse.success(adminUserService.createUser(req));
    }

    /** 사용자 정보 수정 (상태·역할·조직 포함) */
    @PutMapping("/{employeeNo}")
    public ApiResponse<AdminUserListItemResponse> updateUser(
            @PathVariable String employeeNo,
            @Valid @RequestBody AdminUserSaveRequest req
    ) {
        return ApiResponse.success(adminUserService.updateUser(employeeNo, req));
    }

    /** 사용자 프로필 사진 업로드(관리자) */
    @PostMapping(value = "/{employeeNo}/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadProfileImage(
            @PathVariable String employeeNo,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        adminUserService.uploadProfileImage(employeeNo, file);
        return ApiResponse.success("OK");
    }

    /** 사용자 완전 삭제 (DB 하드 삭제) */
    @DeleteMapping("/{employeeNo}")
    public ApiResponse<String> deleteUser(@PathVariable String employeeNo) {
        adminUserService.deleteUser(employeeNo);
        return ApiResponse.success("삭제되었습니다.");
    }
}
