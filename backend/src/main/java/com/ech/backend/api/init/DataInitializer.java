package com.ech.backend.api.init;

import com.ech.backend.domain.retention.RetentionPolicy;
import com.ech.backend.domain.retention.RetentionPolicyRepository;
import com.ech.backend.domain.retention.RetentionResourceType;
import com.ech.backend.domain.settings.AppSetting;
import com.ech.backend.domain.settings.AppSettingKey;
import com.ech.backend.domain.settings.AppSettingRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 애플리케이션 구동 시 테스트 계정의 초기 비밀번호를 자동으로 설정한다.
 *
 * <p>비밀번호가 없는(null) 사용자에 한해 한 번만 설정되며, 이미 설정된 경우 덮어쓰지 않는다.
 * <ul>
 *   <li>기본 비밀번호: 설정 키 {@code auth.initial-password-plaintext} (미생성 시 {@code Test1234!})</li>
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

    @Value("${app.ai.allow-external-llm:false}")
    private String seedAiAllowExternalLlm;

    @Value("${app.ai.policy-version:2026-05-06}")
    private String seedAiPolicyVersion;

    @Value("${app.ai.chat-max-requests-per-minute:30}")
    private String seedAiChatRlPerMinute;

    @Value("${app.ai.chat-max-requests-per-hour:300}")
    private String seedAiChatRlPerHour;

    @Value("${app.ai.llm-max-input-chars:8000}")
    private String seedAiGatewayLlmMaxInputChars;

    @Value("${app.ai.llm.http-enabled:false}")
    private String seedAiLlmHttpEnabled;

    @Value("${app.ai.llm.base-url:}")
    private String seedAiLlmBaseUrl;

    @Value("${app.ai.llm.api-key:}")
    private String seedAiLlmApiKey;

    @Value("${app.ai.llm.model:gpt-4o-mini}")
    private String seedAiLlmModel;

    @Value("${app.ai.llm.max-tokens:1024}")
    private String seedAiLlmMaxTokens;

    @Value("${app.ai.proactive.dismiss-cooldown-hours:24}")
    private String seedAiProactiveDismissCooldownHours;

    @Value("${app.ai.proactive.max-suggestions-per-channel-hour:30}")
    private String seedAiProactiveMaxPerChannelHour;

    @Value("${app.ai.proactive.activity-min-messages-per-hour:5}")
    private String seedAiProactiveActivityMinPerHour;

    @Value("${app.ai.proactive.jobs-enabled:true}")
    private String seedAiProactiveJobsEnabled;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RetentionPolicyRepository retentionPolicyRepository;
    private final AppSettingRepository appSettingRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RetentionPolicyRepository retentionPolicyRepository,
            AppSettingRepository appSettingRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.retentionPolicyRepository = retentionPolicyRepository;
        this.appSettingRepository = appSettingRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initDefaultRetentionPolicies();
        initDefaultAppSettings();
        initDefaultPasswords();
        ensureChannelTypeConstraintAllowsDm();
    }

    private void initDefaultPasswords() {
        List<User> usersWithoutPassword = userRepository.findUsersWithoutPassword();
        if (usersWithoutPassword.isEmpty()) {
            return;
        }
        String plain = appSettingRepository
                .findByKey(AppSettingKey.AUTH_INITIAL_PASSWORD_PLAINTEXT)
                .map(AppSetting::getValue)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(DEFAULT_PASSWORD);
        String encodedDefault = passwordEncoder.encode(plain);
        for (User user : usersWithoutPassword) {
            user.setPasswordHash(encodedDefault);
        }
        userRepository.saveAll(usersWithoutPassword);
        log.info("[DataInitializer] 비밀번호 미설정 사용자 {}명에게 초기 비밀번호 적용 완료. (설정 키: {})",
                usersWithoutPassword.size(), AppSettingKey.AUTH_INITIAL_PASSWORD_PLAINTEXT);
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
        seedSetting(AppSettingKey.AUTH_INITIAL_PASSWORD_PLAINTEXT, DEFAULT_PASSWORD,
                "비밀번호가 없는 사용자에게 기동 시 적용되는 초기 평문 비밀번호. 이미 해시가 있는 계정은 변경되지 않음.");
        seedSetting(AppSettingKey.AI_GATEWAY_ALLOW_EXTERNAL_LLM, seedAiAllowExternalLlm,
                "AI 게이트웨이: 공용 인터넷 LLM 전송 허용(true/false). false 권장.");
        seedSetting(AppSettingKey.AI_GATEWAY_POLICY_VERSION, seedAiPolicyVersion,
                "AI 게이트웨이 정책 표시 버전(클라이언트 노출용 문자열).");
        seedSetting(AppSettingKey.AI_GATEWAY_CHAT_MAX_REQUESTS_PER_MINUTE, seedAiChatRlPerMinute,
                "AI 게이트웨이 chat 분당 호출 상한(0=비활성).");
        seedSetting(AppSettingKey.AI_GATEWAY_CHAT_MAX_REQUESTS_PER_HOUR, seedAiChatRlPerHour,
                "AI 게이트웨이 chat 시간당 호출 상한(0=비활성).");
        seedSetting(AppSettingKey.AI_GATEWAY_LLM_MAX_INPUT_CHARS, seedAiGatewayLlmMaxInputChars,
                "PII 마스킹 후 LLM으로 보낼 프롬프트 최대 코드포인트 수(256~8000). 초과 시 코드포인트 경계에서 잘림.");
        seedSetting(AppSettingKey.AI_LLM_HTTP_ENABLED, seedAiLlmHttpEnabled,
                "OpenAI 호환 HTTP LLM 호출 활성화(true/false). base-url·api-key와 함께 사용.");
        seedSetting(AppSettingKey.AI_LLM_BASE_URL, seedAiLlmBaseUrl == null ? "" : seedAiLlmBaseUrl,
                "LLM API 베이스 URL(예: https://api.openai.com). 끝 슬래시 없이.");
        seedSetting(AppSettingKey.AI_LLM_API_KEY, seedAiLlmApiKey == null ? "" : seedAiLlmApiKey,
                "LLM Bearer 토큰. 관리자만 조회 가능하나 화면 노출에 유의. 비우면 yml/환경변수 폴백.");
        seedSetting(AppSettingKey.AI_LLM_MODEL, seedAiLlmModel,
                "chat/completions model 이름.");
        seedSetting(AppSettingKey.AI_LLM_MAX_TOKENS, seedAiLlmMaxTokens,
                "요청 max_tokens 정수.");
        seedSetting(AppSettingKey.AI_PROACTIVE_DISMISS_COOLDOWN_HOURS, seedAiProactiveDismissCooldownHours,
                "프로액티브 제안함 항목 거절 후 동일 사용자에게 새 제안 적재 금지 시간(시간 단위, 1~168).");
        seedSetting(AppSettingKey.AI_PROACTIVE_MAX_PER_CHANNEL_PER_HOUR, seedAiProactiveMaxPerChannelHour,
                "동일 채널 기준 1시간 롤링 창에서 허용하는 프로액티브 제안 최대 건수(1~500).");
        seedSetting(AppSettingKey.AI_PROACTIVE_ACTIVITY_MIN_MESSAGES_PER_HOUR, seedAiProactiveActivityMinPerHour,
                "프로액티브 활동 힌트: 최근 1시간 타임라인 메시지가 이 건수 이상일 때 채널 관리자에게 힌트 적재(1~500).");
        seedSetting(AppSettingKey.AI_PROACTIVE_JOBS_ENABLED, seedAiProactiveJobsEnabled,
                "프로액티브 스케줄 작업(활동 힌트·다이제스트) 활성화(true/false).");
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

    /**
     * 과거 DB에서 channels_channel_type_check가 PUBLIC/PRIVATE만 허용하는 경우
     * DM 채널 생성이 실패하므로 기동 시 안전하게 보정한다.
     */
    private void ensureChannelTypeConstraintAllowsDm() {
        try {
            List<String> constraintNames = jdbcTemplate.query(
                    """
                            SELECT tc.constraint_name
                            FROM information_schema.table_constraints
                            WHERE tc.table_schema = current_schema()
                              AND tc.table_name = 'channels'
                              AND tc.constraint_type = 'CHECK'
                              AND EXISTS (
                                  SELECT 1
                                  FROM information_schema.constraint_column_usage ccu
                                  WHERE ccu.constraint_schema = tc.constraint_schema
                                    AND ccu.constraint_name = tc.constraint_name
                                    AND ccu.table_name = tc.table_name
                                    AND ccu.column_name = 'channel_type'
                              )
                            """,
                    (rs, rowNum) -> rs.getString(1)
            );
            for (String name : new ArrayList<>(constraintNames)) {
                if (name == null || !name.matches("[A-Za-z0-9_]+")) {
                    continue;
                }
                jdbcTemplate.execute("ALTER TABLE channels DROP CONSTRAINT " + name);
            }
            Integer hasNewConstraint = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM information_schema.table_constraints
                            WHERE table_schema = current_schema()
                              AND table_name = 'channels'
                              AND constraint_name = 'channels_channel_type_check'
                            """,
                    Integer.class
            );
            if (hasNewConstraint == null || hasNewConstraint <= 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE channels ADD CONSTRAINT channels_channel_type_check " +
                                "CHECK (channel_type IN ('PUBLIC','PRIVATE','DM'))"
                );
            }
            log.info("[DataInitializer] channels_channel_type_check 제약을 DM 허용으로 보정했습니다.");
        } catch (Exception e) {
            log.warn("[DataInitializer] channels channel_type 제약 보정 실패: {}", e.getMessage());
        }
    }
}
