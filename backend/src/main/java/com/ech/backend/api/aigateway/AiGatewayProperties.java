package com.ech.backend.api.aigateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 7 AI 게이트웨이 거버넌스·레이트리밋·(선택) LLM HTTP 설정.
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiGatewayProperties {

    /**
     * When false (default), collaboration-era prompts must not be forwarded to a public-internet LLM.
     */
    private boolean allowExternalLlm = false;

    /** Published policy surface string returned by {@code GET /api/ai/gateway/status}. */
    private String policyVersion = "2026-05-06";

    /** 0 disables per-minute limiting. */
    private int chatMaxRequestsPerMinute = 30;

    /** 0 disables per-hour limiting. */
    private int chatMaxRequestsPerHour = 300;

    /**
     * Max Unicode code points sent to LLM after masking ({@code app.ai.llm-max-input-chars}).
     * Values outside 256–8000 are clamped when applied.
     */
    private int llmMaxInputChars = 8000;

    private LlmHttp llm = new LlmHttp();

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

    public int getChatMaxRequestsPerMinute() {
        return chatMaxRequestsPerMinute;
    }

    public void setChatMaxRequestsPerMinute(int chatMaxRequestsPerMinute) {
        this.chatMaxRequestsPerMinute = chatMaxRequestsPerMinute;
    }

    public int getChatMaxRequestsPerHour() {
        return chatMaxRequestsPerHour;
    }

    public void setChatMaxRequestsPerHour(int chatMaxRequestsPerHour) {
        this.chatMaxRequestsPerHour = chatMaxRequestsPerHour;
    }

    public int getLlmMaxInputChars() {
        return llmMaxInputChars;
    }

    public void setLlmMaxInputChars(int llmMaxInputChars) {
        if (llmMaxInputChars < 256) {
            this.llmMaxInputChars = 256;
        } else if (llmMaxInputChars > 8000) {
            this.llmMaxInputChars = 8000;
        } else {
            this.llmMaxInputChars = llmMaxInputChars;
        }
    }

    public LlmHttp getLlm() {
        if (llm == null) {
            llm = new LlmHttp();
        }
        return llm;
    }

    public void setLlm(LlmHttp llm) {
        this.llm = llm != null ? llm : new LlmHttp();
    }

    public static class LlmHttp {
        /** When true and baseUrl+apiKey present, {@link com.ech.backend.api.aigateway.llm.OpenAiCompatibleLlmClient} is used. */
        private boolean httpEnabled = false;

        private String baseUrl = "";
        private String apiKey = "";
        private String model = "gpt-5-mini";
        private int maxTokens = 1024;

        public boolean isHttpEnabled() {
            return httpEnabled;
        }

        public void setHttpEnabled(boolean httpEnabled) {
            this.httpEnabled = httpEnabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl == null ? "" : baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey == null ? "" : apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model == null || model.isBlank() ? "gpt-5-mini" : model;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens <= 0 ? 1024 : maxTokens;
        }
    }
}
