package com.ech.backend.api.settings;

import com.ech.backend.api.settings.dto.AppSettingResponse;
import com.ech.backend.api.settings.dto.CreateSettingRequest;
import com.ech.backend.api.settings.dto.UpdateSettingRequest;
import com.ech.backend.domain.settings.AppSetting;
import com.ech.backend.domain.settings.AppSettingKey;
import com.ech.backend.domain.settings.AppSettingRepository;
import com.ech.backend.domain.user.UserRepository;
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
    private final UserRepository userRepository;

    public AppSettingsService(AppSettingRepository settingRepository, UserRepository userRepository) {
        this.settingRepository = settingRepository;
        this.userRepository = userRepository;
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
    public AppSettingResponse create(CreateSettingRequest request) {
        String key = request.key().trim();
        if (settingRepository.findByKey(key).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 설정 키입니다: " + key);
        }
        Long updatedByUserId = request.updatedBy() == null
                ? null
                : userRepository.findByEmployeeNo(request.updatedBy().trim())
                        .map(u -> u.getId())
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.updatedBy()));
        AppSetting created = new AppSetting(key, request.value(), request.description(), updatedByUserId);
        settingRepository.save(created);
        return toResponse(created);
    }

    @Transactional
    public AppSettingResponse update(String key, UpdateSettingRequest request) {
        AppSetting setting = settingRepository.findByKey(key)
                .orElseThrow(() -> new IllegalArgumentException("설정을 찾을 수 없습니다: " + key));
        Long updatedByUserId = request.updatedBy() == null
                ? null
                : userRepository.findByEmployeeNo(request.updatedBy())
                        .map(u -> u.getId())
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.updatedBy()));
        setting.update(request.value(), request.description(), updatedByUserId);
        settingRepository.save(setting);
        return toResponse(setting);
    }

    private AppSettingResponse toResponse(AppSetting s) {
        return new AppSettingResponse(s.getId(), s.getKey(), s.getValue(),
                s.getDescription(),
                s.getUpdatedBy() == null ? null : userRepository.findById(s.getUpdatedBy()).map(u -> u.getEmployeeNo()).orElse(null),
                s.getUpdatedAt());
    }
}
