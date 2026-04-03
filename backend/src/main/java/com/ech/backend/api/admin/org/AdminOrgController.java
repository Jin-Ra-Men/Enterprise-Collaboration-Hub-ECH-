package com.ech.backend.api.admin.org;

import com.ech.backend.api.admin.org.dto.OrgGroupResponse;
import com.ech.backend.api.admin.org.dto.OrgGroupSaveRequest;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/org-groups")
@RequireRole(AppRole.ADMIN)
public class AdminOrgController {

    private final AdminOrgService adminOrgService;

    public AdminOrgController(AdminOrgService adminOrgService) {
        this.adminOrgService = adminOrgService;
    }

    /** 전체 조직 그룹 목록 조회 */
    @GetMapping
    public ApiResponse<List<OrgGroupResponse>> listOrgGroups() {
        return ApiResponse.success(adminOrgService.getAllOrgGroups());
    }

    /** 조직 그룹 생성 */
    @PostMapping
    public ApiResponse<OrgGroupResponse> createOrgGroup(@Valid @RequestBody OrgGroupSaveRequest req) {
        return ApiResponse.success(adminOrgService.createOrgGroup(req));
    }

    /** 조직 그룹 수정 (표시명·상위조직·정렬순서·활성여부) */
    @PutMapping("/{groupCode}")
    public ApiResponse<OrgGroupResponse> updateOrgGroup(
            @PathVariable String groupCode,
            @Valid @RequestBody OrgGroupSaveRequest req
    ) {
        return ApiResponse.success(adminOrgService.updateOrgGroup(groupCode, req));
    }

    /** 조직 그룹 삭제 (하위 그룹·멤버 연쇄 삭제) */
    @DeleteMapping("/{groupCode}")
    public ApiResponse<String> deleteOrgGroup(@PathVariable String groupCode) {
        adminOrgService.deleteOrgGroup(groupCode);
        return ApiResponse.success("삭제되었습니다.");
    }
}
