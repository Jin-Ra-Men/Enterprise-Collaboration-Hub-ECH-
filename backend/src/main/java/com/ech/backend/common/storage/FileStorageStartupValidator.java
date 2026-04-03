package com.ech.backend.common.storage;

import com.ech.backend.api.settings.AppSettingsService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 첨부파일 저장 경로가 존재·쓰기 가능한지 기동 시 검사한다.
 * 운영에서 {@code FILE_STORAGE_DIR} 만 맞추고 DB {@code file.storage.base-dir} 가 예전 값이거나,
 * 폴더 권한이 없을 때 업로드가 전부 실패하는 경우를 로그로 바로 드러낸다.
 */
@Component
public class FileStorageStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(FileStorageStartupValidator.class);
    private static final String WRITE_PROBE = ".ech-storage-write-probe";

    private final AppSettingsService appSettingsService;

    public FileStorageStartupValidator(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateOnReady() {
        String baseDir = appSettingsService.getFileStorageDir();
        if (baseDir == null || baseDir.isBlank()) {
            log.error("[ECH] file storage dir is blank — channel file uploads will fail.");
            return;
        }
        Path root = Paths.get(baseDir.trim()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            Path probe = root.resolve(WRITE_PROBE);
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            log.info("[ECH] file storage ready: {}", root);
        } catch (IOException e) {
            log.error(
                    "[ECH] file storage NOT writable: {} — fix app_settings key "
                            + "'file.storage.base-dir' or env FILE_STORAGE_DIR, and grant the backend "
                            + "Windows service account write access to this folder. Error: {}",
                    root,
                    e.getMessage()
            );
        }
    }
}
