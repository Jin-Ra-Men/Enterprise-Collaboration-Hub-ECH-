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

    /**
     * 비밀번호가 없는 사용자에게 기동 시 한 번 적용되는 초기 평문 비밀번호.
     * 관리자 설정에서 변경 가능. 값이 비어 있으면 내장 기본값을 사용한다.
     */
    public static final String AUTH_INITIAL_PASSWORD_PLAINTEXT = "auth.initial-password-plaintext";

    // --- AI 게이트웨이 (app.ai.* 와 동일 의미; 기초설정에서 관리자가 덮어쓸 수 있음) ---

    public static final String AI_GATEWAY_ALLOW_EXTERNAL_LLM = "ai.gateway.allow-external-llm";

    public static final String AI_GATEWAY_POLICY_VERSION = "ai.gateway.policy-version";

    public static final String AI_GATEWAY_CHAT_MAX_REQUESTS_PER_MINUTE = "ai.gateway.chat-max-requests-per-minute";

    public static final String AI_GATEWAY_CHAT_MAX_REQUESTS_PER_HOUR = "ai.gateway.chat-max-requests-per-hour";

    public static final String AI_LLM_HTTP_ENABLED = "ai.llm.http-enabled";

    public static final String AI_LLM_BASE_URL = "ai.llm.base-url";

    public static final String AI_LLM_API_KEY = "ai.llm.api-key";

    public static final String AI_LLM_MODEL = "ai.llm.model";

    public static final String AI_LLM_MAX_TOKENS = "ai.llm.max-tokens";
}
