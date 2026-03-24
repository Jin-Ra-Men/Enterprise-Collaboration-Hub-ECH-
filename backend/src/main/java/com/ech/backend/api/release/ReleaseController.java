package com.ech.backend.api.release;

import com.ech.backend.api.release.dto.ActivateReleaseRequest;
import com.ech.backend.api.release.dto.DeploymentHistoryResponse;
import com.ech.backend.api.release.dto.ReleaseVersionResponse;
import com.ech.backend.api.release.dto.RollbackRequest;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/releases")
@RequireRole(AppRole.ADMIN)
public class ReleaseController {

    private final ReleaseService releaseService;

    public ReleaseController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    /**
     * WAR/JAR 파일 업로드.
     * Content-Type: multipart/form-data
     * 파라미터: version (버전 문자열), file (파일), description (선택)
     */
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReleaseVersionResponse> upload(
            @RequestParam String version,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long uploadedBy
    ) throws IOException {
        return ApiResponse.success(
                releaseService.upload(version, description, uploadedBy, file));
    }

    /**
     * 전체 릴리즈 목록 조회 (최신 업로드 순).
     */
    @GetMapping
    public ApiResponse<List<ReleaseVersionResponse>> listAll() {
        return ApiResponse.success(releaseService.listAll());
    }

    /**
     * 릴리즈 단건 조회.
     */
    @GetMapping("/{releaseId}")
    public ApiResponse<ReleaseVersionResponse> getById(@PathVariable Long releaseId) {
        return ApiResponse.success(releaseService.getById(releaseId));
    }

    /**
     * 버전 활성화 (ACTIVE 전환).
     * 기존 ACTIVE 버전은 자동으로 PREVIOUS 처리된다.
     */
    @PostMapping("/{releaseId}/activate")
    public ApiResponse<ReleaseVersionResponse> activate(
            @PathVariable Long releaseId,
            @RequestBody ActivateReleaseRequest request
    ) {
        return ApiResponse.success(releaseService.activate(releaseId, request));
    }

    /**
     * 롤백: 가장 최근의 PREVIOUS 버전을 다시 ACTIVE 전환.
     */
    @PostMapping("/rollback")
    public ApiResponse<ReleaseVersionResponse> rollback(
            @RequestBody RollbackRequest request
    ) {
        return ApiResponse.success(releaseService.rollback(request));
    }

    /**
     * 배포 이력 조회 (최신 순).
     */
    @GetMapping("/history")
    public ApiResponse<List<DeploymentHistoryResponse>> getHistory() {
        return ApiResponse.success(releaseService.getHistory());
    }

    /**
     * 릴리즈 파일 삭제. ACTIVE/PREVIOUS 상태는 삭제 불가.
     */
    @DeleteMapping("/{releaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long releaseId,
            @RequestParam(required = false) Long actorUserId
    ) throws IOException {
        releaseService.delete(releaseId, actorUserId);
    }
}
