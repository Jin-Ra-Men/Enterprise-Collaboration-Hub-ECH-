package com.ech.backend.api.aigateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 7 AI 게이트웨이 거버넌스 플래그.
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiGatewayProperties {

    /**
     * When false (default), collaboration-era prompts must not be forwarded to a public-internet LLM.
     * All {@code POST /api/ai/gateway/chat} calls receive HTTP 403 from policy enforcement.
     */
    private boolean allowExternalLlm = false;

    /** Published policy surface string returned by {@code GET /api/ai/gateway/status}. */
    private String policyVersion = "2026-05-06";

    public boolean isAllowExternalLlm() {
        return allowExternalLlm;
    }

    public void setAllowExternalLlm(boolean allowExternalLlm) {
        this.allowExternalLlm = allowExternalLlm;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }
}
