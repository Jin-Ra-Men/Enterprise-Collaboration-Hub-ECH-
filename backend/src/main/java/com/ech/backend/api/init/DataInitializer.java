package com.ech.backend.api.init;

import com.ech.backend.domain.retention.RetentionPolicy;
import com.ech.backend.domain.retention.RetentionPolicyRepository;
import com.ech.backend.domain.retention.RetentionResourceType;
import com.ech.backend.domain.settings.AppSetting;
import com.ech.backend.domain.settings.AppSettingKey;
import com.ech.backend.domain.settings.AppSettingRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 애플리케이션 구동 시 테스트 계정의 초기 비밀번호를 자동으로 설정한다.
 *
 * <p>비밀번호가 없는(null) 사용자에 한해 한 번만 설정되며, 이미 설정된 경우 덮어쓰지 않는다.
 * <ul>
 *   <li>기본 비밀번호: {@code Test1234!}</li>
 * </ul>
 *
 * <p><b>운영 전환 시 주의:</b> 그룹웨어 인증(GroupwareAuthProvider) 도입 후에는
 * 로컬 비밀번호가 의미 없어지므로 이 초기화 로직을 비활성화하거나 삭제한다.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DEFAULT_PASSWORD = "Test1234!";

    @Value("${app.file-storage-dir:D:/testStorage}")
    private String defaultFileStorageDir;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RetentionPolicyRepository retentionPolicyRepository;
    private final AppSettingRepository appSettingRepository;

    public DataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RetentionPolicyRepository retentionPolicyRepository,
            AppSettingRepository appSettingRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.retentionPolicyRepository = retentionPolicyRepository;
        this.appSettingRepository = appSettingRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initDefaultPasswords();
        initDefaultRetentionPolicies();
        initDefaultAppSettings();
    }

    private void initDefaultPasswords() {
        List<User> usersWithoutPassword = userRepository.findUsersWithoutPassword();
        if (usersWithoutPassword.isEmpty()) {
            return;
        }
        String encodedDefault = passwordEncoder.encode(DEFAULT_PASSWORD);
        for (User user : usersWithoutPassword) {
            user.setPasswordHash(encodedDefault);
        }
        userRepository.saveAll(usersWithoutPassword);
        log.info("[DataInitializer] 비밀번호 미설정 사용자 {}명에게 기본 비밀번호 적용 완료. (Test1234!)",
                usersWithoutPassword.size());
    }

    /**
     * 기본 보존 정책을 시드한다. 이미 존재하는 정책은 덮어쓰지 않는다.
     * <ul>
     *   <li>MESSAGES: 365일, 비활성</li>
     *   <li>AUDIT_LOGS: 180일, 비활성</li>
     *   <li>ERROR_LOGS: 90일, 비활성</li>
     * </ul>
     */
    private void initDefaultRetentionPolicies() {
        seedPolicy(RetentionResourceType.MESSAGES, 365, false,
                "채널 메시지 보존 정책. 만료 시 archived_at 설정(소프트 아카이브).");
        seedPolicy(RetentionResourceType.AUDIT_LOGS, 180, false,
                "감사 이벤트 로그 보존 정책. 만료 시 물리 삭제.");
        seedPolicy(RetentionResourceType.ERROR_LOGS, 90, false,
                "운영 오류 로그 보존 정책. 만료 시 물리 삭제.");
    }

    /**
     * 앱 전역 설정 기본값을 시드한다. 이미 존재하는 설정은 덮어쓰지 않는다.
     */
    private void initDefaultAppSettings() {
        seedSetting(AppSettingKey.FILE_STORAGE_DIR, defaultFileStorageDir,
                "첨부파일 저장 기본 경로. 변경 즉시 반영(재기동 불필요). 절대 경로 권장.");
        seedSetting(AppSettingKey.FILE_MAX_SIZE_MB, "100",
                "단일 첨부파일 최대 업로드 크기(MB).");
    }

    private void seedSetting(String key, String value, String description) {
        if (appSettingRepository.findByKey(key).isEmpty()) {
            appSettingRepository.save(new AppSetting(key, value, description));
            log.info("[DataInitializer] 앱 설정 기본값 생성: {} = {}", key, value);
        }
    }

    private void seedPolicy(RetentionResourceType type, int retentionDays,
                            boolean isEnabled, String description) {
        String typeName = type.name();
        if (retentionPolicyRepository.findByResourceType(typeName).isEmpty()) {
            retentionPolicyRepository.save(
                    new RetentionPolicy(typeName, retentionDays, isEnabled, description));
            log.info("[DataInitializer] 보존 정책 기본값 생성: {} ({}일, enabled={})",
                    typeName, retentionDays, isEnabled);
        }
    }
}
