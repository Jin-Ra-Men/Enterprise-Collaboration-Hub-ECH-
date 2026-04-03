package com.ech.backend.common.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Electron 자동 업데이트용 정적 파일 (latest.yml, 설치 exe).
 * 내부망에서 GitHub에 접근할 수 없을 때 {@code ech-server.json} 의 updateBaseUrl 또는
 * {@code serverUrl + "/desktop-updates/"} 로 이 경로를 가리킨다.
 */
@Configuration
public class DesktopUpdateResourceConfig implements WebMvcConfigurer {

    @Value("${app.releases-dir:./releases}")
    private String releasesDir;

    /** 비어 있으면 {@code {releases-dir}/desktop} */
    @Value("${app.desktop-update-dir:}")
    private String desktopUpdateDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir;
        if (desktopUpdateDir != null && !desktopUpdateDir.isBlank()) {
            dir = Paths.get(desktopUpdateDir.trim()).toAbsolutePath().normalize();
        } else {
            dir = Paths.get(releasesDir).resolve("desktop").toAbsolutePath().normalize();
        }
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            /* 디렉터리 생성 실패 시에도 핸들러 등록 — 요청 시 404 */
        }
        String loc = dir.toUri().toString();
        if (!loc.endsWith("/")) {
            loc += "/";
        }
        registry.addResourceHandler("/desktop-updates/**")
                .addResourceLocations(loc);
    }
}
