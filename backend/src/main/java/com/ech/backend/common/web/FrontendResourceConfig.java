package com.ech.backend.common.web;

import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트 정적 파일 노출.
 *
 * <p>Spring Boot 기본 설정은 {@code /**} 에 정적 리소스 핸들러를 붙여,
 * 일부 환경에서 {@code /api/**} 요청이 컨트롤러가 아닌 리소스 핸들러로 처리되어
 * {@code NoResourceFoundException}(「요청한 경로를 찾을 수 없습니다: api/...」)이 난다.
 *
 * <p>그래서 기본 정적 매핑은 {@code application.yml} 에서 끄고,
 * 실제로 서빙할 파일 경로만 아래처럼 제한한다.
 */
@Configuration
public class FrontendResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String frontendDir = Path.of("..", "frontend").toAbsolutePath().normalize().toUri().toString();
        if (!frontendDir.endsWith("/")) {
            frontendDir += "/";
        }
        registry.addResourceHandler("/index.html", "/styles.css", "/app.js")
                .addResourceLocations(frontendDir, "classpath:/static/", "classpath:/public/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/index.html");
    }
}
