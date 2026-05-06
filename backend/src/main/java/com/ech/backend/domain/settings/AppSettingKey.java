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

    /** Upper bound on Unicode code points sent to the LLM after PII masking (256–8000). */
    public static final String AI_GATEWAY_LLM_MAX_INPUT_CHARS = "ai.gateway.llm-max-input-chars";

    public static final String AI_LLM_HTTP_ENABLED = "ai.llm.http-enabled";

    public static final String AI_LLM_BASE_URL = "ai.llm.base-url";

    public static final String AI_LLM_API_KEY = "ai.llm.api-key";

    public static final String AI_LLM_MODEL = "ai.llm.model";

    public static final String AI_LLM_MAX_TOKENS = "ai.llm.max-tokens";

    /** Hours to suppress new proactive suggestions after user dismisses one (Phase 7-3-2). */
    public static final String AI_PROACTIVE_DISMISS_COOLDOWN_HOURS = "ai.proactive.dismiss-cooldown-hours";

    /** Max proactive inbox rows per channel per rolling hour (enqueue guard). */
    public static final String AI_PROACTIVE_MAX_PER_CHANNEL_PER_HOUR = "ai.proactive.max-suggestions-per-channel-hour";

    /** Minimum timeline-visible messages in the last hour to enqueue an activity hint per opted-in channel. */
    public static final String AI_PROACTIVE_ACTIVITY_MIN_MESSAGES_PER_HOUR = "ai.proactive.activity-min-messages-per-hour";

    /** When false, hourly/digest proactive schedulers no-op (ops toggle via app_settings). */
    public static final String AI_PROACTIVE_JOBS_ENABLED = "ai.proactive.jobs-enabled";

    /**
     * When false, LLM 기반 대화 인사이트(일정·워크플로 제안) 스케줄러가 동작하지 않는다.
     * 외부 LLM 미구성·정책 차단 시에도 내부에서 무동작 처리된다.
     */
    public static final String AI_PROACTIVE_LLM_CONVERSATION_INSIGHT_ENABLED =
            "ai.proactive.llm-conversation-insight-enabled";

    /** LLM이 반환한 confidence 최소값(0~1). 미만이면 적재하지 않는다. */
    public static final String AI_PROACTIVE_LLM_CONVERSATION_CONFIDENCE_MIN =
            "ai.proactive.llm-conversation-confidence-min";

    /** 채널당 롤링 1시간 동안 허용하는 LLM 호출 최대 횟수(비용 상한). */
    public static final String AI_PROACTIVE_LLM_CONVERSATION_MAX_LLM_CALLS_PER_CHANNEL_PER_HOUR =
            "ai.proactive.llm-conversation-max-llm-calls-per-channel-hour";
}
