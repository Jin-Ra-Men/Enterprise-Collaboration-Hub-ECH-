package com.ech.backend.api.aigateway;

/**
 * Effective AI gateway knobs (YAML/env defaults merged with {@code app_settings} overrides).
 */
public interface AiGatewayConfigurable {

    boolean isAllowExternalLlm();

    String getPolicyVersion();

    int getChatMaxRequestsPerMinute();

    int getChatMaxRequestsPerHour();

    boolean isLlmHttpEnabled();

    String getLlmBaseUrl();

    String getLlmApiKey();

    String getLlmModel();

    int getLlmMaxTokens();

    /**
     * Maximum Unicode code points forwarded to the LLM after masking (clamped 256–8000).
     * Longer prompts are truncated at code-point boundaries with an ellipsis suffix.
     */
    int getLlmMaxInputChars();

    /** True when HTTP LLM outbound is fully configured (enabled + base URL + API key). */
    boolean isLlmHttpConfigured();
}
