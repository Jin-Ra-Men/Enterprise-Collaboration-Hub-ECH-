package com.ech.backend.api.settings;

import com.ech.backend.api.settings.dto.AppSettingResponse;
import com.ech.backend.api.settings.dto.UpdateSettingRequest;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
@RequireRole(AppRole.ADMIN)
public class AppSettingsController {

    private final AppSettingsService appSettingsService;

    public AppSettingsController(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    /**
     * 전체 설정 목록 조회.
     */
    @GetMapping
    public ApiResponse<List<AppSettingResponse>> listAll() {
        return ApiResponse.success(appSettingsService.listAll());
    }

    /**
     * 특정 키의 설정 조회.
     */
    @GetMapping("/{key}")
    public ApiResponse<AppSettingResponse> getByKey(@PathVariable String key) {
        return ApiResponse.success(appSettingsService.getByKey(key));
    }

    /**
     * 설정 값 수정. 변경 즉시 다음 파일 업로드부터 반영된다.
     * 서버 재기동 불필요.
     */
    @PutMapping("/{key}")
    public ApiResponse<AppSettingResponse> update(
            @PathVariable String key,
            @Valid @RequestBody UpdateSettingRequest request
    ) {
        return ApiResponse.success(appSettingsService.update(key, request));
    }
}
