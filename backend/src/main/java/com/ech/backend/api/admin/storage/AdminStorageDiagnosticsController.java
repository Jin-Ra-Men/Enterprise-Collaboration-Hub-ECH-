package com.ech.backend.api.admin.storage;

import com.ech.backend.api.admin.storage.dto.StorageProbeResponse;
import com.ech.backend.api.settings.AppSettingsService;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import com.ech.backend.common.storage.FileStorageAccessProbe;
import java.nio.file.Path;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 첨부파일 저장 경로(로컬·UNC)가 <strong>백엔드 JVM 프로세스</strong> 기준으로 쓰기 가능한지 진단한다.
 * PowerShell에서 관리자로 {@code Test-Path} 가 성공해도, NSSM 서비스가 Local System이면 UNC에 실패할 수 있다.
 */
@RestController
@RequestMapping("/api/admin/storage")
@RequireRole(AppRole.ADMIN)
public class AdminStorageDiagnosticsController {

    private final AppSettingsService appSettingsService;

    public AdminStorageDiagnosticsController(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    @GetMapping("/probe")
    public ApiResponse<StorageProbeResponse> probe() {
        String configured = appSettingsService.getFileStorageDir();
        FileStorageAccessProbe.Result r = FileStorageAccessProbe.probe(configured);
        Path p = r.resolvedAbsolutePath();
        String pathStr = p != null ? p.toString() : "";
        return ApiResponse.success(new StorageProbeResponse(
                pathStr,
                r.writable(),
                FileStorageAccessProbe.looksLikeUnc(p),
                r.detail()
        ));
    }
}
