package com.ech.backend.api.orgsync;

import com.ech.backend.api.orgsync.dto.OrgSyncPreviewResponse;
import com.ech.backend.api.orgsync.dto.OrgSyncResultResponse;
import com.ech.backend.api.orgsync.dto.OrgSyncSource;
import com.ech.backend.api.orgsync.dto.UpdateUserStatusRequest;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/org-sync")
@RequireRole(AppRole.ADMIN)
public class OrgSyncController {

    private final OrgSyncService orgSyncService;

    public OrgSyncController(OrgSyncService orgSyncService) {
        this.orgSyncService = orgSyncService;
    }

    @GetMapping("/users")
    public ApiResponse<OrgSyncPreviewResponse> previewUsers(
            @RequestParam(defaultValue = "TEST") OrgSyncSource source
    ) {
        return ApiResponse.success(orgSyncService.preview(source));
    }

    @PostMapping("/users/sync")
    public ApiResponse<OrgSyncResultResponse> syncUsers(
            @RequestParam(defaultValue = "TEST") OrgSyncSource source
    ) {
        return ApiResponse.success(orgSyncService.syncUsers(source));
    }

    @PutMapping("/users/{employeeNo}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable String employeeNo,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        orgSyncService.updateUserStatus(employeeNo, request.status());
        return ApiResponse.success(null);
    }
}
