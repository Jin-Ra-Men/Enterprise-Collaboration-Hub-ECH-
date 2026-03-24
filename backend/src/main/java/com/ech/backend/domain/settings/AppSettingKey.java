package com.ech.backend.domain.settings;

/**
 * 앱 전역 설정 키 상수.
 * DB의 app_settings.setting_key 값과 일치해야 한다.
 */
public final class AppSettingKey {

    private AppSettingKey() {}

    /** 첨부파일 저장 기본 경로 (절대 경로 권장) */
    public static final String FILE_STORAGE_DIR = "file.storage.base-dir";

    /** 단일 첨부파일 최대 크기 (MB) */
    public static final String FILE_MAX_SIZE_MB = "file.max-size-mb";
}
