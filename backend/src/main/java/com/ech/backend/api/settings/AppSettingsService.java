package com.ech.backend.api.settings;

import com.ech.backend.api.settings.dto.AppSettingResponse;
import com.ech.backend.api.settings.dto.UpdateSettingRequest;
import com.ech.backend.domain.settings.AppSetting;
import com.ech.backend.domain.settings.AppSettingKey;
import com.ech.backend.domain.settings.AppSettingRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppSettingsService {

    /** application.yml 기본값 — DB에 설정이 없을 때 폴백 */
    @Value("${app.file-storage-dir:D:/testStorage}")
    private String defaultFileStorageDir;

    private final AppSettingRepository settingRepository;

    public AppSettingsService(AppSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    /**
     * 현재 유효한 파일 저장 경로를 반환한다.
     * DB 설정(app_settings) 우선, 없으면 application.yml 기본값 사용.
     */
    public String getFileStorageDir() {
        return settingRepository.findByKey(AppSettingKey.FILE_STORAGE_DIR)
                .map(AppSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(defaultFileStorageDir);
    }

    /**
     * 특정 설정 키의 값을 반환한다.
     */
    public String get(String key, String defaultValue) {
        return settingRepository.findByKey(key)
                .map(AppSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public List<AppSettingResponse> listAll() {
        return settingRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AppSettingResponse getByKey(String key) {
        return settingRepository.findByKey(key)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("설정을 찾을 수 없습니다: " + key));
    }

    @Transactional
    public AppSettingResponse update(String key, UpdateSettingRequest request) {
        AppSetting setting = settingRepository.findByKey(key)
                .orElseThrow(() -> new IllegalArgumentException("설정을 찾을 수 없습니다: " + key));
        setting.update(request.value(), request.description(), request.updatedBy());
        settingRepository.save(setting);
        return toResponse(setting);
    }

    private AppSettingResponse toResponse(AppSetting s) {
        return new AppSettingResponse(s.getId(), s.getKey(), s.getValue(),
                s.getDescription(), s.getUpdatedBy(), s.getUpdatedAt());
    }
}
